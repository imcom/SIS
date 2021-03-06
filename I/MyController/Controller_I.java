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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;
import java.util.Timer;


/**
*
* @author Jinyu
*/
public class Controller_I {
	
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
	final byte[] SND_LOCK = new byte[0];
	final byte[] RCV_LOCK = new byte[0];
	private boolean SEND_AVA = true;
	final int hidBegin = 6, hidEnd = 38;// hidEnd 待定，取出id使用,分离请求部分使用
	
	public static void main(String[] args) throws IOException{
		Controller_I conn = new Controller_I(args);
	}

	public Controller_I(String[] args) throws IOException{
		serialManager = new SerialPortManager(args);
		serialManager.setVisible(true);
		serialManager.setBackground(Color.gray);
		serialManager.repaint();
		while(!serialManager.isOpen){
			//waiting until port is open
		}
		os = getOS();
		is = getIS();
		/*
		* server thread only start in inner ProxyServer!!!!!!!!!!!!!!!!
		*/

		http_server = new SocketServer();
		t_httpserver = new Thread(http_server, "SocketServer");
		t_httpserver.start();
		System.out.println("http server start");
		//////////////////////////

		//FTP
		ServerSocket ss = new ServerSocket();//FTP服务器等待客户端连接
		ss.bind(new InetSocketAddress(InetAddress.getLocalHost().getHostName(), 2121));
		ListenSocket ls = new ListenSocket(ss);
		System.out.println("ftp server start");
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
				Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
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
				Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
			}
			tmap.put(this.key, this);
			synchronized(this){
				try {
					wait();
				} catch (InterruptedException ex) {
					Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
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
				Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
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
		
		//----------------------------------------------
		
		class MyTimer implements Runnable {
			Timer timer = null;
			String target_id = null;
			public MyTimer(){
				
				timer = new Timer();
				timer.schedule(new MyTask2(), 2000000);
			}
			public MyTimer(String tid){
				timer = new Timer();
				target_id = tid;
				timer.schedule(new MyTask1(target_id), 2000000);
			}
			public void run(){
				while(true){
					//System.out.println("Timer start to count");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				}
			}
		}

		class MyTask1 extends java.util.TimerTask {
			String id = null;
			public MyTask1(String tid){
				id = tid;
			}
			public void run(){
				synchronized(tmap.get(id)){
					tmap.get(id).notify();
				}
				System.out.println("Timer called: " + id);
			}
		}
		
		class MyTask2 extends java.util.TimerTask {
			//String id = null;
			public MyTask2(){
				//id = tid;
			}
			public void run(){
				response += "##END##";
				
			}
		}
		//-----------------------------------------------

		public SocketHandler(Socket socket) throws IOException{
			this.socket = socket;
			this.clientInput=socket.getInputStream();
			this.clientOutput=socket.getOutputStream();
		}//init() end

		public void run(){
			int k = 0;
			byte bbyte[] = new byte[20000000];
			BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput));
			try {
				request = reader.readLine();
				
			} catch (IOException ex) {
				Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
			}
			this.key = request.substring(hidBegin, hidEnd)+"HTTP";
			try {
				
				while(true){
					if(SEND_AVA){
						send(request);
						break;
					}
					Thread.sleep(200);
				}
			} catch (Exception ex) {
				Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
			}
			
			tmap.put(key, this);
			synchronized(this){
				try {
					System.out.println("SocketHandler wait");
					wait();
				} catch (InterruptedException ex) {
					Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
			tmap.remove(key);
			serialManager.ONFILE = true;
			try {
				//while(true){
				//	if(SEND_AVA){
				send("#HRDY#" + this.key + "#END#");
				//break;
				//}
				//Thread.sleep(200);
				//}
			} catch (Exception ex) {
				Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
			}

			MyTimer mt = new MyTimer();
			Thread tmt = new Thread(mt);
			tmt.start();
			int i = 0;
			
			//String temp = "";
			do{
				int inbytes = 0;
				byte abyte[] = new byte[1024];
				
				
				try {
					while (is.available() > 0) {
						
						inbytes = is.read(abyte);
						
						//response += new String(abyte, "utf-8");
						
						for( int j = 0; j < inbytes;){
							bbyte[k++] = abyte[j++];
						}
						
						/// System.out.println("ininininiresponse:"+ inbytes);
						//System.out.println("kcontent :"+ new String(bbyte));
						response += new String(abyte);

						
						
						if (response.contains("##END##")) {
							//System.out.println("$$$$$!!!" + response);
							i = 1;
							break;
						}
					}
				} catch (IOException ex) {
					Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
				}
				if( i == 1) {
					//System.out.println("length :"+);
					response = new String(bbyte,0,k);
					k = 0;
					//System.out.println("reponse$$$$$$$ :"+response);
					break;}
			}while(true);
			response = response.substring(0, response.indexOf("##END##"));
			

			//System.out.println("http server recieve: " + response);
			synchronized(RCV_LOCK){
				RCV_LOCK.notify();
			}
			serialManager.ONFILE = false;
			PrintWriter printWriter = new PrintWriter(clientOutput,true);
			
			printWriter.println(response);
			try {
				clientInput.close();
				clientOutput.close();
				socket.close();
			} catch (IOException ex) {
				Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
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
					Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
				}
				System.out.println("Server Accpeted Request!");
				Thread sh = null;
				try {
					sh = new Thread(new RmailHandler(clientSocket));
				} catch (IOException ex) {
					Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
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
					Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
				}
				System.out.println("Server Accpeted Request!");
				Thread sh = null;
				try {
					sh = new Thread(new SmailHandler(clientSocket));
				} catch (IOException ex) {
					Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
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
					Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
				}
				System.out.println("Server Accpeted Request!");
				Thread sh = null;
				try {
					sh = new Thread(new SocketHandler(clientSocket));
				} catch (IOException ex) {
					Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
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
		boolean floop = true;

		//---------------------------------------------------------------------------------------


		class MyTimer implements Runnable {
			Timer timer = null;
			String target_id = null;
			public MyTimer(){
				
				timer = new Timer();
				timer.schedule(new MyTask1(), 500000);
			}
			public void run(){
				while(true){
					//System.out.println("Timer start to count");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				}
			}
		}

		class MyTask1 extends java.util.TimerTask {
			//String id = null;
			public MyTask1(){
				//id = tid;
			}
			public void run(){
				floop = false;
			}
		}



		//----------------------------------------------------------------------------------------


		public Invoker(){
		}

		public void run(){
			while(true){
				if(serialManager.DA){
					
					do{
						inputBuffer += serialManager.getMessage();
					}while(!inputBuffer.contains("#END#"));
					inputBuffer = inputBuffer.trim();
					
				}
				if(inputBuffer.startsWith("#HREQ#")){//#HTTP REQUEST#
					HttpHandler httpHandler = new HttpHandler(inputBuffer.substring(5));
					Thread t_http = new Thread(httpHandler);
					t_http.start();
				}else if(inputBuffer.startsWith("#FILE#")){//保持不能放
					//notify ftp file 准备接受文件
					synchronized(RCV_LOCK){
						String mapkey = inputBuffer.substring(6,inputBuffer.length()-5);		
						while(true){
							try{
								synchronized(tmap.get(mapkey))
								{
									tmap.get(mapkey).notify();
									System.out.println("FILE NOTIFY");
									break;
								}
							}catch(Exception e){
								try {
									Thread.sleep(200);
								} catch (InterruptedException ex) {
									Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
								}
							}
						}
						try {
							
							RCV_LOCK.wait(); //RCV_LOCK.notify();
							
						} catch (InterruptedException ex) {
							Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
						}
					}
				}else if(inputBuffer.startsWith("#PFTP#")){//#FTP#
					//找标志，唤醒线程，按照标志存入命令
					MyTimer fmt = new MyTimer();
					Thread tfmt = new Thread(fmt);
					tfmt.start();
					String buff = inputBuffer.substring(6, inputBuffer.length()-5);
					String key = buff.substring(buff.indexOf("#")+1, buff.lastIndexOf("#"));
					String cmd = buff.substring(buff.lastIndexOf("#")+1);
					System.out.println( "PFTP receive:"+ cmd);
					System.out.println("PFTP:"+ key);
					ftp_cmd.put(key,cmd);
					while(floop){
						try{
							synchronized(tmap.get(key)){
								tmap.get(key).notify();
								
								break;
							}
						}catch(Exception e){
							try {
								
								Thread.sleep(200);
							} catch (Exception ex) {
								Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
							}
						}
					}
					if(floop == false){
						ftp_cmd.remove(key);
						floop = true;
					}
					//tmap.remove(key);
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
						}catch(Exception e)
						{
							//e.printStackTrace();
							try {
								
								Thread.sleep(2000);
							} catch (Exception ex) {
								Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
							}
						}
					}
					//tmap.remove(mapkey);
				}else if(inputBuffer.startsWith("#HRES#")){//#HTTP RESPONSE#
					synchronized(RCV_LOCK){
						String key = inputBuffer.substring(hidBegin, hidEnd)+"HTTP";
						
						while(true){
							try{
								synchronized(tmap.get(key)){
									tmap.get(key).notify();
									
									break;
								}
							}catch(Exception e){
								
								try {
									Thread.sleep(2000);
								} catch (InterruptedException ex) {
									Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
								}
							}
						}
						try {
							
							RCV_LOCK.wait();
						} catch (InterruptedException ex) {
							Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
						}
						
					}
				}else if(inputBuffer.startsWith("#MSND#")){
					//send email
					//SmtpHandler smtpHandler = new SmtpHandler(inputBuffer);
					//Thread t_smtp = new Thread(smtpHandler);
					//t_smtp.start();
				}else if(inputBuffer.startsWith("#MRCV#")){
					//recieve email
				}else if(inputBuffer.startsWith("#MRES#")){
					//response email
					String key = inputBuffer.substring(hidBegin, hidEnd)+"MAIL";
					http_res.put(key, inputBuffer.substring(hidEnd, inputBuffer.length()-5));
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
								Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
							}
						}
					}
				}
				//清空输入缓冲区
				inputBuffer = "";
				//
				try {
					synchronized(this){
						wait(5000);
					}
				} catch (InterruptedException ex) {
					Logger.getLogger(Controller_I.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}//class invoker

	class SmtpHandler implements Runnable{
		public SmtpHandler(String arg){

		}

		public void run(){

		}
	}// smtp handler

	class HttpHandler implements Runnable{

		public HttpHandler(String arg){

		}

		public void run(){

		}
	}// http handler

	/**
	* FTP Proxy Server I
	* @author nikko
	*/
	class ListenSocket implements Runnable{
		private ServerSocket serversocket;
		public ListenSocket(ServerSocket serversocket){
			this.serversocket = serversocket;
			Thread thread = new Thread(this);
			thread.start();
		}
		public void run(){
			while(true){
				try{
					Socket clientsocket = serversocket.accept();
					String msg = clientsocket.getInetAddress().getHostAddress()+":"+clientsocket.getPort();
					System.out.println(msg);
					Thread thread = new Thread(new Proxy_in(clientsocket, msg));
					thread.start();
				}catch(Exception e){
					System.out.println(e.getMessage());
				}
			}
		}
	}// class listen socket

	class Proxy_in implements Runnable {

		private Socket cs;//和客户端控制传输
		BufferedReader inData;
		PrintWriter outData;

		private Socket ds;//和客户端数据传输

		String mapkey;
		String ftpCmd = "";
		String temp="";

		//----------------------------------------------------------------------------


		class MyTimer implements Runnable {
			Timer timer = null;
			
			public MyTimer(){
				
				timer = new Timer();
				timer.schedule(new MyTask1(), 800000);
			}
			public void run(){
				while(true){
					//System.out.println("Timer start to count");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				}
			}
		}

		class MyTask1 extends java.util.TimerTask {
			
			public MyTask1(){
				
			}
			public void run(){
				if(ftpCmd.equalsIgnoreCase("feat")){
					temp = "211 End";
				}else if(ftpCmd.equalsIgnoreCase("mlsd")){
					temp = "226 Transfer OK";
				}
			}
		}


		//------------------------------------------------------------------------------



		public Proxy_in(Socket cs, String key)
		{
			this.cs = cs;
			this.mapkey = key;
		}


		public void run()
		{
			try{

				inData = new BufferedReader(new InputStreamReader(cs.getInputStream(),"gb2312"));
				outData = new PrintWriter(new BufferedWriter(new OutputStreamWriter(cs.getOutputStream(),"gb2312")),true);

				/*串口*/
				String alltemp = "#PFTP#"+"#START#"+mapkey+"#END#";//刚要连接
				
				
				
				/*发送连接信息, 把KEY也发过去*/
				

				synchronized(this){
					tmap.put(mapkey, this);
					while(true){
						if(SEND_AVA){
							send(alltemp);//send(request);
							break;
						}
						Thread.sleep(200);
					}
					wait();
				}
				System.out.println("Proxy-in send START");
				System.out.println("FTP准备接收");
				alltemp = (String) ftp_cmd.get(mapkey);//收服务器信息
				ftp_cmd.remove(mapkey);
				tmap.remove(mapkey);
				/*串口接信息*/

				outData.println(alltemp);//写回给客户端
				System.out.println(alltemp);
				System.out.println("proxy_in 6 send it");

				

				while(!(ftpCmd.toLowerCase().startsWith("quit")))
				{
					try{
						ftpCmd = inData.readLine();
						System.out.println("proxy_in 1 receive");
						System.out.println("status :"+ftpCommand(ftpCmd));//LIST wenjian transfer need

					}catch (IOException ex){
						ex.printStackTrace();
						ftpCmd = "quit";
					}
				}

				System.out.println("quit process finished");

				try{
					cs.close();
				}catch(IOException e){
					e.printStackTrace();
				}

				cs = null;
				inData = null;
				outData = null;
			}catch(Exception e){
				e.printStackTrace();
			}
		}

		synchronized String ftpCommand(String cmd) throws IOException, InterruptedException{

			/*收到的指令 串口发*/
			
			
			String regex = "[45][0-9][0-9] ";
			System.out.println("proxy_in send it :" + cmd);
			//outData.println(in.readLine());
			//if(cmd == null) cmd = "";
			String ftpCmd = cmd.toLowerCase();
			while(true){
				if(SEND_AVA){
					send("#PFTP#"+"#"+mapkey+"#"+cmd+"#END#");//send(request);
					break;
				}
				Thread.sleep(200);
			}
			try{
				
				if(ftpCmd.contains("mlst")){
					System.out.println("mlst process");
					while(true){
						
						tmap.put(mapkey, this);
						synchronized (this){
							
							wait();
						}
						String qtemp = (String) ftp_cmd.get(mapkey);
						ftp_cmd.remove(mapkey);
						tmap.remove(mapkey);
						System.out.println("ftpsession5 receive it "+ qtemp);
						outData.println(qtemp);
						System.out.println("ftpsession6 send it"+ qtemp);
						String subtemp = qtemp.substring(0, 4);
						if(qtemp.startsWith("250 ")||subtemp.matches(regex))

						break;
					}
					
					return "mlst";
				}
				else
				if(ftpCmd.equals("feat")){
					System.out.println("feat process");
					temp = "";
					MyTimer mt1 = new MyTimer();
					Thread tmt1 = new Thread(mt1);
					tmt1.start();
					while(true){
						
						tmap.put(mapkey, this);
						synchronized (this){
							
							wait();
						}
						temp = (String) ftp_cmd.get(mapkey);
						ftp_cmd.remove(mapkey);
						tmap.remove(mapkey);
						System.out.println("ftpsession5 receive it "+ temp);
						outData.println(temp);
						System.out.println("ftpsession6 send it"+ temp);
						String subtemp = temp.substring(0, 4);
						if(temp.startsWith("211 ")||subtemp.matches(regex)||temp.startsWith("250 "))
						//if(temp.startsWith("211 ")||temp.startsWith("502 "))

						break;
					}
					
					return "feat";
				}
				else
				if(ftpCmd.equals("pasv")){
					try{
						InetAddress addr = InetAddress.getLocalHost();

						ServerSocket ser_ds = new ServerSocket(); //给客户端发送
						ser_ds.bind(new InetSocketAddress(addr.getHostAddress().toString(),0));

						System.out.println(ser_ds.getInetAddress().getHostAddress()+":"+ser_ds.getLocalPort());

						tmap.put(mapkey, this);
						synchronized (this){
							wait();
						}
						String pasv = (String) ftp_cmd.get(mapkey);
						ftp_cmd.remove(mapkey);
						tmap.remove(mapkey);
						//String pasv = in.readLine();/*从串口读控制信息*/
						

						int a = ser_ds.getLocalPort();
						int c = a%256;
						int d = a-c;
						int e = d/256;

						StringTokenizer arg = new StringTokenizer(ser_ds.getInetAddress().getHostAddress(),".",false);
						String clientIP = "";
						clientIP = arg.nextToken();
						for(int i = 0;i<3;i++){
							String hIP = arg.nextToken();
							clientIP = clientIP + "," + hIP;
						}

						System.out.println("ftpsession6 send "+"227 Entering Passive Mode ("+clientIP+","+e+","+c+")");
						outData.println("227 Entering Passive Mode ("+clientIP+","+e+","+c+")");

						ds = ser_ds.accept();
						System.out.println("accept OK");

						return "PASV";
					}
					catch(Exception e){
						outData.println("500 Port number syntax.");
						System.out.println("PASV 500 Port number syntax.");
						return "ERROR PASV";
					}
				}

				else if("LIST".equals(cmd)){

					System.out.println("LIST process");
					tmap.put(mapkey, this);
					synchronized (this){
						wait();
					}
					String receive = (String) ftp_cmd.get(mapkey);
					ftp_cmd.remove(mapkey);//接收 150
					tmap.remove(mapkey);

					System.out.println("ftpsession 6 send " + receive);
					outData.println(receive);

					//BufferedReader datain = new BufferedReader(new InputStreamReader(daids.getInputStream(),"gb2312"));
					PrintWriter daout = new PrintWriter(new BufferedWriter(new OutputStreamWriter(ds.getOutputStream(),"gb2312")),true);

					String temp;

					do
					{
						tmap.put(mapkey, this);
						synchronized (this){
							wait();
						}
						temp = (String) ftp_cmd.get(mapkey);
						ftp_cmd.remove(mapkey);
						tmap.remove(mapkey);
						//temp  = datain.readLine();/*串口读数据*/
						System.out.println("ftpsession data 5 "+temp);
						if(temp.equals("null"))  break;
						daout.println(temp);
						//if(temp == null)  break;
					}while(!temp.equals("null"));

					daout.flush();
					daout.close();
					System.out.println("ready to receive ");
					tmap.put(mapkey, this);
					synchronized (this){
						wait();
					}
					receive = (String) ftp_cmd.get(mapkey);
					ftp_cmd.remove(mapkey);
					tmap.remove(mapkey);
					//receive = in.readLine();//对外读  需要判断
					System.out.println("ftpsession 6 send " + receive);
					outData.println(receive);
					return "LIST";
				}
				else if("MLSD".equals(cmd)){

					System.out.println("MLSD process");
					tmap.put(mapkey, this);
					synchronized (this){
						wait();
					}
					String receive = (String) ftp_cmd.get(mapkey);
					ftp_cmd.remove(mapkey);//接收 150
					tmap.remove(mapkey);

					System.out.println("ftpsession 6 send " + receive);
					outData.println(receive);

					//BufferedReader datain = new BufferedReader(new InputStreamReader(daids.getInputStream(),"gb2312"));
					PrintWriter daout = new PrintWriter(new BufferedWriter(new OutputStreamWriter(ds.getOutputStream(),"gb2312")),true);

					temp="";

					MyTimer mt2 = new MyTimer();
					Thread tmt2 = new Thread(mt2);
					tmt2.start();

					do
					{
						tmap.put(mapkey, this);
						synchronized (this){
							wait();
						}
						temp = (String) ftp_cmd.get(mapkey);
						ftp_cmd.remove(mapkey);
						tmap.remove(mapkey);
						//temp  = datain.readLine();/*串口读数据*/
						System.out.println("ftpsession data 5 "+temp);
						if(temp.equals("null"))  break;
						daout.println(temp);
					}while(!temp.equals("null"));

					daout.flush();
					daout.close();
					System.out.println("ready to receive ");
					tmap.put(mapkey, this);
					synchronized (this){
						wait();
					}
					receive = (String) ftp_cmd.get(mapkey);
					ftp_cmd.remove(mapkey);
					tmap.remove(mapkey);
					//receive = in.readLine();//对外读  需要判断
					System.out.println("ftpsession 6 send " + receive);
					outData.println(receive);
					return "MLSD";
				}

				else if(cmd.indexOf("RETR")!= -1){//下载

					System.out.println("RETR process");
					tmap.put(mapkey, this);
					synchronized (this){
						wait();
					}
					String receive = (String) ftp_cmd.get(mapkey);
					ftp_cmd.remove(mapkey);//接收 150
					tmap.remove(mapkey);
					System.out.println("ftpsession 6 send " + receive);
					outData.println(receive);


					tmap.put(mapkey, this);
					synchronized(this){//等FILE唤醒
						wait();
					}
					tmap.remove(mapkey);

					serialManager.ONFILE = true;
					int inbytes;

					try{
						InputStream datain = getIS();
						/*从串口读*/
						OutputStream daout = ds.getOutputStream();
						String temp;
						while(true){
							if(SEND_AVA){
								send("#FRDY#"+mapkey+"#END#");//send(request);
								break;
							}
							Thread.sleep(200);
						}
						
						int i = 0;
						System.out.println("ftp file come in");
						do{
							
							byte abyte[] = new byte[1024];
							while(is.available()>0){
								inbytes = is.read(abyte);
								
								temp = new String(abyte,"utf-8");
								
								
								if(temp.contains("#FILEND#")){
									
									i = 1;
									break;}
								daout.write(abyte,0,inbytes);
							}
							//serialManager.ONFILE = true;
							if( i == 1) break;
							/*temp = serialManager.getMessage();
									
																		
									//System.out.println("read "+ temp);
									if(temp.matches("###FILEEND###")){
										i = 1;
										break;}
																		//if(!temp.equals(""))
																		if(!temp.equals(""))
									daout.write(temp.getBytes());*/
							//daout.write(SND_LOCK);
							
						}while(true);
						
						
						daout.flush();
						daout.close();
						serialManager.ONFILE = false;
						synchronized(RCV_LOCK){
							RCV_LOCK.notify();
							System.out.println("wo JIAO la");
						}//唤醒接收

						System.out.println("ready to receive ");
						tmap.put(mapkey, this);
						synchronized (this){
							wait();
						}
						receive = (String) ftp_cmd.get(mapkey);
						ftp_cmd.remove(mapkey);
						tmap.remove(mapkey);
						//receive = in.readLine();//对外读  需要判断
						System.out.println("ftpsession 6 send " + receive);
						outData.println(receive);
						return "RETR";
					}catch(Exception e){
						e.printStackTrace();
					}
				}

				else if(cmd.indexOf("STOR")!= -1){

					System.out.println("STOR process");
					//String receive = in.readLine();//接收 150
					tmap.put(mapkey, this);
					synchronized (this){
						wait();
					}
					String receive = (String) ftp_cmd.get(mapkey);
					ftp_cmd.remove(mapkey);
					tmap.remove(mapkey);
					System.out.println("ftpsession 6 send " + receive);
					outData.println(receive);

					while(true){
						if(SEND_AVA){
							send("#FILE#"+mapkey+"#END#"); 
							break;
						}
						//Thread.sleep(200);
						//System.out.println("ni ge SB1!!!!!!");
					}
					
					
					tmap.put(mapkey, this);//等待READ信号
					synchronized(this){
						
						wait();
					}
					tmap.remove(mapkey);
					
					InputStream datain = ds.getInputStream();
					
					int inbytes;
					do{
						byte abyte[] = new byte[1024];
						inbytes = datain.read(abyte);
						if(inbytes != -1){
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
						SEND_AVA = true;
					}

					System.out.println("ready to receive ");
					//receive = in.readLine();//对外读  需要判断
					tmap.put(mapkey, this);
					synchronized (this){
						wait();
					}
					receive = (String) ftp_cmd.get(mapkey);
					ftp_cmd.remove(mapkey);
					tmap.remove(mapkey);
					System.out.println("ftpsession 6 send " + receive);
					outData.println(receive);
					return "STOR";

					
				}

				
				//String temp = in.readLine();
				tmap.put(mapkey, this);
				synchronized (this){
					wait();
				}
				String temp = (String) ftp_cmd.get(mapkey);
				ftp_cmd.remove(mapkey);
				tmap.remove(mapkey);
				System.out.println("ftpsession5 receive it "+ temp);
				outData.println(temp);
				System.out.println("ftpsession6 send it");
				return "不需要控制";

			}catch(Exception e)
			{
				System.out.println("550 Command module error" + e);
				e.printStackTrace();
				return "ERROR";
			}
		}

	}// proxy_in end
}
