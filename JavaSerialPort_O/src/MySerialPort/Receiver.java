package MySerialPort;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.lang.Character;

import java.awt.Panel;
import java.awt.TextArea;
import java.awt.BorderLayout;

import javax.comm.SerialPort;

public class Receiver extends Panel implements Runnable{
	private final int rows = 6;
	private final int cols = 40;
	private TextArea displayArea;
	private ByteStatistics counter;
	private SerialConnection parent;
	private byte[] inputBuffer;
	private int textCount;
	
	public Receiver(SerialConnection parent){
		super();
		this.parent = parent;
		
		this.inputBuffer = new byte[1024];
		this.setLayout(new BorderLayout());
		
		this.counter = new ByteStatistics("Bytes Received", 10, parent.serialPort, true);
		this.add("South", this.counter);
		
		this.displayArea = new TextArea(rows, cols);
		this.displayArea.setEditable(false);
		this.add("Center", displayArea);

		this.textCount = 0;
	}
	
	public void setPort(SerialPort port){
		this.counter.setPort(port);
	}
	
	public void showValues(){
		this.counter.showValues();
	}
	
	public void clearValues(){
		this.displayArea.setText("");
		this.counter.clearValues();
	}
	
	public void run(){
		while (this.parent.isopen){
			try{
				synchronized (this) {
					wait(10000);
				}
			}
			catch (InterruptedException e){}

			if (this.parent.ctlSigs.DA){
                                String ts = readData();
                                System.out.println("RCV 去你mb: " + ts);
				this.parent.retrieveMessage(ts);
			}
		}//while end
	}//run end
	
	public void setBitsPerCharacter(int val){
		this.counter.setBitsPerCharacter(val);
	}
	
	private String displayText(byte[] bytes, int byteCount) throws UnsupportedEncodingException{
		String	str = "";

		if (this.textCount > 10000){
			this.displayArea.setText("");
			this.textCount = 0;
		}

                str += new String(bytes, 0, byteCount, "utf-8");
		this.displayArea.append(str);

		this.counter.incrementValue((long)byteCount);

		this.textCount += byteCount;

		return str;
	}

	public String readData(){
		String str = "";
		int bytes;
		try{
		    while (this.parent.isopen && (this.parent.is.available() > 0 )){
                            bytes = this.parent.is.read(this.inputBuffer);
                            System.out.println("原始: "+new String(inputBuffer, "utf-8"));
				if (bytes > 0){
					if (bytes > this.inputBuffer.length){
						    System.out.println(parent.serialPort.getName() + ": Input buffer overflow!");
					}
					str += this.displayText(this.inputBuffer, bytes);   
				}//if end
		    }//while end
			parent.ctlSigs.DA = false;
			parent.ctlSigs.showErrorValues();
			return str;
		} catch (IOException ex){
			System.out.println(parent.serialPort.getName() + ": Cannot read input stream");
                        return str;
		}
	}
}