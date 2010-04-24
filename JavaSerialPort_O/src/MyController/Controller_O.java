/*
* To change this template, choose Tools | Templates
* and open the template in the editor.
*/

package MyController;

import MySerialPort.SerialPortManager;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

/**
*
* @author Jinyu
*/
public class Controller_O {
	
	public SerialPortManager serialManager;
	public String inputBuffer = "";
	public Thread t_invoker;
	private Invoker invoker;
	private SocketServer http_server;
	public Thread t_httpserver;
	private InputStream is = null;
	private OutputStream os = null;
	Map tmap = new HashMap();
	Map ftp_cmd = new HashMap();
	Map http_res = new HashMap();
	private boolean SEND_AVA = true;
	final byte[] SND_LOCK = new byte[0];
	final byte[] RCV_LOCK = new byte[0];
	final byte[] OS_LOCK = new byte[0];
	final byte[] IS_LOCK = new byte[0];
	final int hidBegin = 6, hidEnd = 38;// hidEnd 待定，取出id使用,分离请求部分使用
	
	public static void main(String[] args) throws IOException{
		Controller_O conn = new Controller_O(args);
	}

	public Controller_O(String[] args) throws IOException{
		serialManager = new SerialPortManager(args);
		serialManager.setVisible(true);
		serialManager.setBackground(Color.gray);
		serialManager.repaint();
		while(!serialManager.isOpen){
			//waiting until port is open
		}
		os = getOS();
		is = getIS();
		// ftp listen init
		clientListen ftp_out = new clientListen();
		System.out.println("ftp server start");
		//////////////////////////
		invoker = new Invoker();
		t_invoker = new Thread(invoker, "INVOKER");
		t_invoker.start();
		System.out.println("invoker start");
		while(true){
			if(t_invoker == null){
				t_invoker = new Thread(invoker, "INVOKER");
				t_invoker.start();
			}
			if(t_invoker != null && serialManager.DA){
				synchronized(invoker){
					invoker.notify();
				}
			}
		}
	}

	class SmailHandler implements Runnable{
		private Socket socket;
		private InputStream  clientInput;
		private OutputStream clientOutput;
		private String to = "", from = "", user = "", passwd = "", host = "";
		private String request = "", key = "", response = "";

		public SmailHandler(Socket socket) throws IOException{
			this.socket = socket;
			this.clientInput=socket.getInputStream();
			this.clientOutput=socket.getOutputStream();
		}

		public void run(){
			BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput));
			try {
				request = reader.readLine();
			} catch (IOException ex) {
				Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
			}
			this.key = request.substring(hidBegin, hidEnd)+"EMAIL";
			try {
				while(true){
					if(SEND_AVA){
						send(request);
						break;
					}
					Thread.sleep(200);
				}
				
			} catch (Exception ex) {
				Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
			}
			tmap.put(this.key, this);
			synchronized(this){
				try {
					wait();
				} catch (InterruptedException ex) {
					Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
			response = (String) http_res.get(this.key);
			http_res.remove(key);
			PrintWriter printWriter = new PrintWriter(clientOutput,true);
			printWriter.println(response);
			try {
				clientInput.close();
				clientOutput.close();
				socket.close();
			} catch (IOException ex) {
				Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}// send mail handler

	class RmailHandler implements Runnable{
		private Socket socket;
		private InputStream  clientInput;
		private OutputStream clientOutput;

		public RmailHandler(Socket socket) throws IOException{
			this.socket = socket;
			this.clientInput=socket.getInputStream();
			this.clientOutput=socket.getOutputStream();
		}
		public void run(){

		}
	}// rcv mail handler

	class SocketHandler implements Runnable{
		private Socket socket;
		private InputStream  clientInput;
		private OutputStream clientOutput;
		private String request = "", response = "";
		private String key = "";

		public SocketHandler(Socket socket) throws IOException{
			this.socket = socket;
			this.clientInput=socket.getInputStream();
			this.clientOutput=socket.getOutputStream();
		}//init() end

		public void run(){
			BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput));
			try {
				request = reader.readLine();
			} catch (IOException ex) {
				Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
			}
			this.key = request.substring(hidBegin, hidEnd)+"HTTP";
			System.out.println("http request key: "+key);
			try {
				System.out.println("http request content: "+request);
				while(true){
					if(SEND_AVA){
						send(request);
						break;
					}
					Thread.sleep(200);
				}
				
			} catch (Exception ex) {
				Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
			}
			tmap.put(key, this);
			synchronized(this){
				try {
					wait();
				} catch (InterruptedException ex) {
					Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
			response = (String) http_res.get(this.key);
			http_res.remove(key);
			PrintWriter printWriter = new PrintWriter(clientOutput,true);
			System.out.println("http response: "+response);
			printWriter.println(response);
			try {
				clientInput.close();
				clientOutput.close();
				socket.close();
			} catch (IOException ex) {
				Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
			}
		}//run() end
	}//socketHandler end
	
	class RcvMailServer implements Runnable{
		ServerSocket rmailSocket;
		Socket clientSocket;
		
		public RcvMailServer() throws IOException{
			rmailSocket = new ServerSocket(2200);
			System.out.println("Server Started Listen On 2200");
			clientSocket = null;
		}
		
		public void run(){
			while(true){
				try {
					clientSocket = rmailSocket.accept();
				} catch (IOException ex) {
					Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
				}
				System.out.println("Server Accpeted Request!");
				Thread sh = null;
				try {
					sh = new Thread(new RmailHandler(clientSocket));
				} catch (IOException ex) {
					Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
				}
				if(sh != null) sh.start();
			}
		}
	}// recieve mail server

	class SendMailServer implements Runnable{
		ServerSocket smailSocket;
		Socket clientSocket;

		public SendMailServer() throws IOException{
			smailSocket = new ServerSocket(2218);
			System.out.println("Server Started Listen On 2218");
			clientSocket = null;
		}

		public void run(){
			while(true){
				try {
					clientSocket = smailSocket.accept();
				} catch (IOException ex) {
					Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
				}
				System.out.println("Server Accpeted Request!");
				Thread sh = null;
				try {
					sh = new Thread(new SmailHandler(clientSocket));
				} catch (IOException ex) {
					Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
				}
				if(sh != null) sh.start();
			}
		}
	}// send mail server

	class SocketServer implements Runnable{
		ServerSocket serverSocket;
		Socket clientSocket;
		public SocketServer() throws IOException{
			serverSocket = new ServerSocket(2202);
			System.out.println("Server Started Listen On 2202");
			clientSocket = null;
		}

		public void run(){
			while(true){
				try {
					clientSocket = serverSocket.accept();
				} catch (IOException ex) {
					Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
				}
				System.out.println("Server Accpeted Request!");
				Thread sh = null;
				try {
					sh = new Thread(new SocketHandler(clientSocket));
				} catch (IOException ex) {
					Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
				}
				if(sh != null) sh.start();
			}
		}
	}//SocketServer end

	synchronized public void send(String msg) throws IOException, InterruptedException{
		if(msg.getBytes("UTF-8").length % 8 != 0){
			byte[] fix = new byte[8 - msg.getBytes("UTF-8").length % 8];
			msg += new String(fix, "utf-8");
		}
		byte[] buf = msg.getBytes("UTF-8");
		System.out.println("send: " + msg);
		if(msg.contains("#FILE#") || msg.contains("#HRES#")){
			synchronized(SND_LOCK){
				SEND_AVA = false;//SND_LOCK.notify();
			}
		}
		serialManager.sendMessage(buf);
		wait(500);// provide a lag
	}

	public InputStream getIS(){
		return serialManager.getIS();
	}

	public OutputStream getOS(){
		return serialManager.getOS();
	}

	class Invoker implements Runnable{
		public Invoker(){
		}
		public void run(){
			while(true){
				if(serialManager.DA){
					System.out.println("data come in");
					do{
						inputBuffer += serialManager.getMessage();
						if(!inputBuffer.equals("")){
							System.out.println("invoker: "+inputBuffer);
						}
						//System.out.println("空");
					}while(!inputBuffer.contains("#END#"));
                                        inputBuffer = inputBuffer.trim();
					//System.out.println("recieve origin: "+inputBuffer);
				}
				if(inputBuffer.startsWith("#HREQ#")){//#HTTP REQUEST#
					HttpHandler httpHandler = new HttpHandler(inputBuffer);
					Thread t_http = new Thread(httpHandler);
					t_http.start();
					System.out.println("http handler thread start!");
				}else if(inputBuffer.startsWith("#FILE#")){//保持不能放
					//notify ftp file 准备接受文件
					synchronized(RCV_LOCK){
						String mapkey = inputBuffer.substring(6,inputBuffer.length()-5);
						while(true){
							try{
								synchronized(tmap.get(mapkey)){
									tmap.get(mapkey).notify();
									System.out.println("file notify");
									break;
								}
							}catch(Exception e){
								try{
									Thread.sleep(200);

								}catch(Exception ex){}
							}
						}
						try {
							System.out.println("wo WAIT la!!!");
							RCV_LOCK.wait(); //RCV_LOCK.notify();
							System.out.println("wo AWAKE le!!!!!");
						} catch (InterruptedException ex) {
							Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
						}
					}
				}else if(inputBuffer.startsWith("#PFTP#")){//#FTP#
					//找标志，唤醒线程，按照标志存入命令
					System.out.println("进入 ftp 命令处理");
					if(inputBuffer.substring(6).startsWith("#START#")){
						System.out.println("FTP 2 RECEIVE "+inputBuffer.substring(13, inputBuffer.length()-5));
						ftp_cmd.put("START", inputBuffer.substring(13, inputBuffer.length()-5));//存入KEY
						while(true){
							try{
								synchronized(tmap.get("START")){
									tmap.get("START").notify();
									break;
								}
							}catch(Exception e){
								try{
									Thread.sleep(200);
								}catch(Exception ex){}
							}
						}
					}
					else{
						String buff = inputBuffer.substring(6, inputBuffer.length()-5);
						String key = buff.substring(buff.indexOf("#")+1, buff.lastIndexOf("#"));
						String cmd = buff.substring(buff.lastIndexOf("#")+1);

						ftp_cmd.put(key,cmd);
						
						while(true){
							try{
								synchronized(tmap.get(key)){
									tmap.get(key).notify();
									break;
								}
							}catch(Exception e){
								try{
									Thread.sleep(200);
								}catch(Exception ex){}
							}
						}
					}
				}else if(inputBuffer.startsWith("#FRDY#")){//#READY#
					//notify ftp file 唤醒发送文件
					String mapkey = inputBuffer.substring(6,inputBuffer.length()-5);
					while(true){
						try{
							synchronized(tmap.get(mapkey)){               
								tmap.get(mapkey).notify();
								System.out.println("FRDY notify");
								break;
							}
						}catch(Exception e){
							try{
								Thread.sleep(200);
							}catch(Exception ex){}
						}
					}
				}else if(inputBuffer.startsWith("#HRES#")){//#HTTP RESPONSE#
				}else if(inputBuffer.startsWith("#MSND#")){
					//send email
					SmtpHandler smtpHandler = new SmtpHandler(inputBuffer.substring(6, inputBuffer.length()-5));
					Thread t_smtp = new Thread(smtpHandler);
					t_smtp.start();
				}else if(inputBuffer.startsWith("#MRCV#")){
					//recieve email
				}else if(inputBuffer.startsWith("#MRES#")){
					//response email
				}else if(inputBuffer.startsWith("#HRDY#")){// 唤醒发送线程
					String key = inputBuffer.substring(hidBegin, hidEnd)+"HTTP";
					while(true){
						try{
							synchronized(tmap.get(key)){
								tmap.get(key).notify();
								break;
							}
						}catch(Exception e){
							try {
								Thread.sleep(200);
							} catch (InterruptedException ex) {
								Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
							}
						}
					}
				}
				inputBuffer = "";//清空输入缓冲区
				try {
					synchronized(this){
						wait(5000);
					}
				} catch (InterruptedException ex) {
					Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}//class invoker

	class SmtpHandler implements Runnable{
		private String to = "", from = "", user = "", passwd = "", host = "", content = "", title = "";
		private String key = "", response = "";
		public SmtpHandler(String args){
			key = args.substring(0, hidEnd-hidBegin)+"MAIL";
			args = args.substring(hidEnd-hidBegin);
			String[] arg = args.split("#");
			to = arg[0];
			from = arg[1];
			user = arg[2];
			passwd = arg[3];
			host = arg[4];
			content = arg[5];
			title = arg[6];
		}

		public void run(){
			SimpleEmail email = new SimpleEmail();
			//设置发送主机的服务器地址
			email.setHostName(this.host);
			try{
				//设置收件人邮箱
				email.addTo(this.to,this.to.substring(0, to.indexOf("@")));
				//发件人邮箱
				email.setFrom(this.from,this.from.substring(0, from.indexOf("@")));
				//如果要求身份验证，设置用户名、密码，分别为发件人在邮件服务器上注册的用户名和密码
				email.setAuthentication(user, passwd);
				//设置邮件的主题
				email.setSubject(title);
				//邮件正文消息
				email.setMsg(content);
				email.send();
				response = "#MRES#" + this.key + "SUCCESS" + "#END#";
				while(true){
					if(SEND_AVA){
						send(response);
						break;
					}
					Thread.sleep(200);
				}
			}catch(Exception e){
				response = "#MRES#" + this.key + "FALIURE" + "#END#";
				try{
					while(true){
						if(SEND_AVA){
							send(response);
							break;
						}
						Thread.sleep(200);
					}
				}catch(Exception ee){}
			}
		}
	}// smtp handler

	class HttpHandler implements Runnable{
		String response = "";
		String hostname = "";
		String uri = "";
		String userid = "";
		String[] PostParams;
		String[] GetParams;
		public HttpHandler(String arg){
			this.userid = arg.substring(hidBegin, hidEnd)+"HTTP";
			this.uri = arg.substring(hidEnd, arg.length()-5);
		}

		public void run(){
			try {
				HttpClient httpclient = new DefaultHttpClient();
				HttpGet httpget = new HttpGet(this.uri);
				HttpResponse httpResponse = httpclient.execute(httpget);
				HttpEntity responseEntity = httpResponse.getEntity();
				httpclient.getConnectionManager().shutdown();
				response = EntityUtils.toString(responseEntity);
				while(true){
					if(SEND_AVA){
						send("#HRES#" + this.userid + "#END#");
						break;
					}
					Thread.sleep(200);
				}
				
				synchronized(this){
					tmap.put(this.userid, this);
					wait();
				}
				response += "##END##";
				if(response.getBytes("utf-8").length % 8 != 0){
					byte[] fix = new byte[8 - response.getBytes("UTF-8").length % 8];
					response += new String(fix, "utf-8");
				}
				byte[] snd_array = response.getBytes("utf-8");
				int i, offset = 1024;
				for(i = 0; i + offset < snd_array.length; i += offset){
					os.write(snd_array, i, offset);
					os.flush();
				}
				if(i < snd_array.length){
					os.write(snd_array, i, snd_array.length-i);
					os.flush();
				}
				synchronized(SND_LOCK){
					SEND_AVA = true;//SND_LOCK.notify();
				}
				//send("#HRES#" + this.userid + response + "#END#");
			} catch (Exception ex) {
				Logger.getLogger(Controller_O.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}// http handler

	/**
	* FTP Proxy Server_O
	* @author nikko
	*/
	class clientListen implements Runnable {

		public clientListen()
		{
			Thread thread = new Thread(this, "nikko_1");
			thread.start();
		}
		public void run()
		{
			while (true){
				try{
					synchronized(this){
						tmap.put("START",this);
						wait();
					}

					String key = (String)ftp_cmd.get("START");

					/*接收串口传来的连接信息
					包括KEY */

					String msg = "connect2 form " + key;/*Get infor form SerPort*/
					System.out.println(msg);


					Thread thread = new Thread(new Proxy_out(key), key);
					thread.start();
				}
				catch (Exception ex)
				{
					System.out.println(ex.getMessage());
				}

				try{ Thread.sleep(500);} catch(Exception ex) {}
			}
		}
	}// clientListen CLASS

	class Proxy_out implements Runnable{

		//ServerSocket serversocket;//代理服务器之间互传

		//  private Socket ss;//代理服务器之间控制
		/// BufferedReader inData;//代理服务器之间控制
		// PrintWriter outData;//代理服务器之间控制

		// private ServerSocket daids; //代理服务器之间数据
		// private Socket daids_client;//代理之间数据

		private Socket outside;//对外控制信息
		BufferedReader ino;//对外控制信息
		PrintWriter outo;//对外控制信息

		private Socket ds;//对外数据

		String mapkey;

		//public FTPClient ftpClient = new FTPClient();

		public Proxy_out(String clientipport) throws IOException
		{
			// this.ss = socket;
			// daids = ds_socket;
			this.mapkey = clientipport;
		}

		public void run() {

			try{
				while(true){
					if(SEND_AVA){
						send("#PFTP#"+"#"+mapkey+"#"+"220 Welcome!!!!#END#");
						break;
					}
					//Thread.sleep(200);
				}
				
				synchronized(this){
					tmap.put(mapkey, this);
					wait();
				}

				String buff = (String) ftp_cmd.get(mapkey);
				ftp_cmd.remove(mapkey);
				tmap.remove(mapkey);
				String address = buff.substring(buff.indexOf("$")+1,buff.lastIndexOf("$"));
				/** 先返回客户端信息* 要求客户姓名* 从姓名分解*/

				outside = new Socket(address,21);//对外
				/*根据用户名分解连接*/

				ino = new BufferedReader(new InputStreamReader(outside.getInputStream(),"gb2312"));
				outo = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outside.getOutputStream(),"gb2312")),true);

				System.out.println("Proxy_out 5 no-send it "+ino.readLine());
				/*此次服务器信息不返回*/

				String username = buff.substring(buff.lastIndexOf("$")+1);
				outo.println("USER "+ username);
				while(true){
					if(SEND_AVA){
						send("#PFTP#"+"#"+mapkey+"#"+ino.readLine()+"#END#");
						break;
					}
					//Thread.sleep(200);
				}
				
			}

			catch (Exception ex){
				Logger.getLogger(Proxy_out.class.getName()).log(Level.SEVERE, null, ex);
			}

			String ftpCmd = "";
			try {
				/*串口读*/
				synchronized(this){
					tmap.put(mapkey, this);
					wait();
				}

				ftpCmd = (String) ftp_cmd.get(mapkey);
				ftp_cmd.remove(mapkey);
				tmap.remove(mapkey);

				System.out.println("proxy_out 2 receive : "+ ftpCmd);

			} catch (Exception ex) {
				Logger.getLogger(Proxy_out.class.getName()).log(Level.SEVERE, null, ex);
			}

			while(!(ftpCmd.toLowerCase().startsWith("quit")))
			{
				try{
					System.out.println("8888888888888888888:"+ftpCmd);
					System.out.println("status: "+ftpCommand(ftpCmd));
					System.out.println("9999999999999999999999999999");
					/*串口读*/
					tmap.put(mapkey, this);
					synchronized(this){					
						wait();
					}
					ftpCmd = (String) ftp_cmd.get(mapkey);
					ftp_cmd.remove(mapkey);
					tmap.remove(mapkey);
					System.out.println("ftpclient2 receive : "+ ftpCmd);

				}catch (Exception ex){
					ex.printStackTrace();
					ftpCmd = "quit";
				}
			}

			System.out.println("quit process");
			outo.println(ftpCmd);
			try {
				String temp = ino.readLine();

				/*串口写*/
				while(true){
					if(SEND_AVA){
						send("#PFTP#"+"#"+mapkey+"#"+temp+"#END#");
						break;
					}
					//Thread.sleep(200);
				}
				
				System.out.println("quit process finished");
			} catch (Exception ex) {
				Logger.getLogger(Proxy_out.class.getName()).log(Level.SEVERE, null, ex);
			}

			try{ Thread.sleep(500);} catch(Exception ex) { ex.printStackTrace();}
		}



		synchronized String ftpCommand(String cmd){
			if(cmd == null) cmd = "";
			StringTokenizer ftpCmdtok = new StringTokenizer(cmd.toLowerCase());
			String ftpCmd = "";
			String receive;
			try{
				ftpCmd = ftpCmdtok.nextToken();
				System.out.println("ftpclient3  send : "+ cmd);
				outo.println(cmd);

				System.out.println("ftpCmd is " + ftpCmd);

				if(ftpCmd.contains("mlst")){
					System.out.println("mlst process");
					String temp;

					do
					{
						temp  = ino.readLine();
						System.out.println("ftpclient data 57 send "+temp);
						/*串口发送*/
						//Thread.sleep(2000);
						while(true){
							if(SEND_AVA){
								send("#PFTP#"+"#"+mapkey+"#"+temp+"#END#");
								break;
							}
							//Thread.sleep(200);
						}

						if(temp.startsWith("250 ")||temp.startsWith("550"))
						{    // Thread.sleep(1000);
							//send("#PFTP#"+"#"+mapkey+"#"+"221 no support"+"#END#");
							break;}
					}while(temp!=null);
					return "mlst";
				}
				else

				if(ftpCmd.equals("feat")){
					System.out.println("feat process");
					String temp;

					do
					{
						temp  = ino.readLine();
						System.out.println("ftpclient data 57 send "+temp);
						/*串口发送*/
						//Thread.sleep(2000);
						while(true){
							if(SEND_AVA){
								send("#PFTP#"+"#"+mapkey+"#"+temp+"#END#");
								break;
							}
							//Thread.sleep(200);
						}
						
						if(temp.startsWith("211 ") || temp.startsWith("502 "))
						{    // Thread.sleep(1000);
							//send("#PFTP#"+"#"+mapkey+"#"+"221 no support"+"#END#");
							break;}
					}while(temp!=null);
					return "feat";
				}
				else
				if(ftpCmd.equals("pasv")){
					try{
						receive = ino.readLine();//对外读  需要判断
						System.out.println("pasv mode " + receive);
						System.out.println("ftpclient3 receive : "+ receive);

						int start = receive.indexOf("(");
						System.out.println("start "+ start);
						int end = receive.indexOf(")");
						System.out.println("end "+ end);
						String ss = receive.substring(start+1, end);

						System.out.println(ss);

						StringTokenizer arg = new StringTokenizer(ss,",",false);

						String ServerIP = "";
						String ipString = "";
						int cmdPort;

						if(ds != null){
							ds.close();
							ds = null;
						}
						ServerIP = arg.nextToken();
						ipString = ServerIP;

						for(int i = 0;i<3;i++){
							String hIP = arg.nextToken();
							ServerIP = ServerIP + "." + hIP;
							ipString = ipString + "," + hIP;
						}

						String p1 = arg.nextToken();
						ipString = ipString + ","+ p1;
						cmdPort = new Integer(p1).intValue();
						cmdPort *= 256;
						String p2 = arg.nextToken();
						ipString = ipString + "," + p2;
						cmdPort += new Integer(p2).intValue();

						System.out.println(ServerIP+":"+cmdPort);

						//daids_client = daids.accept();//代理之间数据
						ds = new Socket(ServerIP, cmdPort);
						System.out.println("ftpclient pasv ds : "+ ServerIP +" : " +cmdPort);

						System.out.println("ftpclient5 send " + receive);
						while(true){
							if(SEND_AVA){
								send("#PFTP#"+"#"+mapkey+"#"+receive+"#END#");
								break;
							}
							//Thread.sleep(200);
						}
						
						//outData.println(receive);

						return "PASV";
					}
					catch(Exception e){
						e.printStackTrace();
						return "PASV ERROR";
					}
				}

				else if("MLSD".equals(cmd)){
					System.out.println("LIST process");
					receive = ino.readLine();//接收 150
					System.out.println("ftpclient5 send " + receive);

					/*往串口发*/
					while(true){
						if(SEND_AVA){
							send("#PFTP#"+"#"+mapkey+"#"+receive+"#END#");
							break;
						}
						//Thread.sleep(200);
					}
					

					BufferedReader datain = new BufferedReader(new InputStreamReader(ds.getInputStream(),"gb2312"));

					String temp;

					do
					{
						temp  = datain.readLine();
						System.out.println("ftpclient data 5 send "+temp);
						/*串口发送*/
						//Thread.sleep(2000);
						while(true){
							if(SEND_AVA){
								send("#PFTP#"+"#"+mapkey+"#"+temp+"#END#");
								break;
							}
							//Thread.sleep(200);
						}
						
					}while(temp!=null);

					receive = ino.readLine();//对外读  需要判断
					System.out.println("ftpclient 5 send " + receive);
					while(true){
						if(SEND_AVA){
							send("#PFTP#"+"#"+mapkey+"#"+receive+"#END#");
							break;
						}
						//Thread.sleep(200);
					}
					
					return "MLSD";
				}

				else if("LIST".equals(cmd)){
					System.out.println("LIST process");
					receive = ino.readLine();//接收 150
					System.out.println("ftpclient5 send " + receive);

					/*往串口发*/
					while(true){
						if(SEND_AVA){
							send("#PFTP#"+"#"+mapkey+"#"+receive+"#END#");
							break;
						}
						//Thread.sleep(200);
					}
					

					BufferedReader datain = new BufferedReader(new InputStreamReader(ds.getInputStream(),"gb2312"));

					String temp;

					do
					{
						temp  = datain.readLine();
						System.out.println("ftpclient data 5 send "+temp);
						/*串口发送*/
						while(true){
							if(SEND_AVA){
								send("#PFTP#"+"#"+mapkey+"#"+temp+"#END#");
								break;
							}
							//Thread.sleep(200);
						}
						
					}while(temp!=null);

					receive = ino.readLine();//对外读  需要判断
					System.out.println("ftpclient 5 send " + receive);
					while(true){
						if(SEND_AVA){
							send("#PFTP#"+"#"+mapkey+"#"+receive+"#END#");
							break;
						}
						//Thread.sleep(200);
					}
					
					return "LIST";
				}

				else if(cmd.indexOf("RETR")!= -1){//下载

					System.out.println("RETR process");
					receive = ino.readLine();//接收 150
					System.out.println("ftpclient5 send " + receive);
					while(true){
						if(SEND_AVA){
							send("#PFTP#"+"#"+mapkey+"#"+receive+"#END#");
							Thread.sleep(100);
							send("#FILE#"+mapkey+"#END#");
							break;
						}
						//Thread.sleep(200);
					}
					

					

					tmap.put(mapkey, this);//等待READ信号
					synchronized(this){
						wait();
					}
					tmap.remove(mapkey);

					InputStream datain = ds.getInputStream();
					//OutputStream daout = getOS();//os instead

					//BufferedReader dain = new BufferedReader(new InputStreamReader(ds.getInputStream()));
					//PrintWriter daout = new PrintWriter(ds.getOutputStream(),true);

					int inbytes;

					do{
						byte abyte[] = new byte[1024];
						inbytes = datain.read(abyte);
						if(inbytes != -1)
						System.out.println("文件内容: " + new String(abyte));
						/*String response = new String(abyte);
						if(response.getBytes("utf-8").length % 8 != 0){
							byte[] fix = new byte[8 - response.getBytes("UTF-8").length % 8];
							response += new String(fix, "utf-8");
						}
						//os = getOS();
						byte[] snd_array = response.getBytes("utf-8");*/

						if(inbytes != -1){
							//os.write(snd_array,0,snd_array.length);
							os.write(abyte,0,inbytes);
							Thread.sleep(200);
						}
					}while(inbytes != -1);
					System.out.println("文件发送完毕");
					//daout.flush();
					Thread.sleep(1000);
					os.write("#FILEND#".getBytes("utf-8"));
					os.flush();
					Thread.sleep(1000);
					synchronized(SND_LOCK){
						SEND_AVA = true;//SND_LOCK.notify();
					}
					System.out.println("ready to receive ");
					receive = ino.readLine();//对外读  需要判断
					System.out.println("ftpclient 5 send " + receive);
					while(true){
						if(SEND_AVA){
							send("#PFTP#"+"#"+mapkey+"#"+receive+"#END#");
							break;
						}
					}
					
					return "RETR";
				}

				else if(cmd.indexOf("STOR")!= -1){

					System.out.println("STOR process");
					receive = ino.readLine();//接收 150
					System.out.println("ftpclient5 send " + receive);
					while(true){
						if(SEND_AVA){
							send("#PFTP#"+"#"+mapkey+"#"+receive+"#END#");
							break;
						}
						//Thread.sleep(200);
					}
					

					tmap.put(mapkey, this);
					synchronized(this){
						wait();
					}
					tmap.remove(mapkey);
                                        serialManager.ONFILE = true;
					//InputStream datain = getIS();
					OutputStream daout = ds.getOutputStream();

					//byte abyte[] = new byte[1024];
					int inbytes;
					String temp;
					//Thread.sleep(100000);
					send("#FRDY#"+mapkey+"#END#");

					//while(true){
						int i = 0;
						//if(serialManager.DA){
                                                do{
                                                      byte abyte[] = new byte[1024];
                                                      while(is.available()>0){
                                                        inbytes = is.read(abyte);
                                                        temp = new String(abyte,"utf-8");
                                                        if(temp.contains("#FILEND#")){
                                                            System.out.println("asd11111");
                                                            i =1;
                                                            break;
                                                        }
                                                        daout.write(abyte,0,inbytes);
                                                      }
							/*System.out.println("ftp file come in");
							do{
								temp = serialManager.getMessage();
								if(!temp.equals(""))
								System.out.println("read"+temp);
								if(temp.matches("###FILEEND###")){
									i = 1;
									break;
								}
								daout.write(temp.getBytes());
							}while(true);*/
						
						if(i == 1)  break;
					}while(true);
					System.out.println("wo chu lai la!!!0000");
					daout.flush();
					daout.close();
                                        serialManager.ONFILE = false;
					synchronized(RCV_LOCK){
						RCV_LOCK.notify();
					}//唤醒接收

					System.out.println("ready to receive ");
					receive = ino.readLine();//对外读  需要判断
					System.out.println("ftpclient 5 send " + receive);
					while(true){
						if(SEND_AVA){
							send("#PFTP#"+"#"+mapkey+"#"+receive+"#END#");
							break;
						}
						//Thread.sleep(200);
					}
					
					return "STOR";
				}
				System.out.println("wu kong zhi");
				//不需要控制
				receive = ino.readLine();//对外读  需要判断
				System.out.println("ftpclient 5 send " + receive);
				while(true){
					if(SEND_AVA){
						send("#PFTP#"+"#"+mapkey+"#"+receive+"#END#");
						break;
					}
					//Thread.sleep(200);
				}
				
				return "不需要控制";

			}catch(Exception e)
			{
				System.out.println("550 Command module error" + e);
				e.printStackTrace();
				return "error";
			}
		}
	}// Proxy_out CLASS
}// controller end
