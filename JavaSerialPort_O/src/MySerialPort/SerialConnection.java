/*
 *	处理串口连接细节的类
 *	保持连接的状态
 *
 */
 
package MySerialPort;

import javax.comm.*;
import java.io.*;
import java.util.TooManyListenersException;
import java.awt.Panel;
import java.awt.BorderLayout;

/**
A class that handles the details of a serial connection. Reads from one 
TextArea and writes to a second TextArea. 
Holds the state of the connection.
*/
public class SerialConnection extends Panel implements SerialPortEventListener, CommPortOwnershipListener {

    private SerialPortManager parent;

    //private TextArea messageAreaOut;
    //private TextArea messageAreaIn;
	
	/*监视界面*/
	public CtlSigDisplay ctlSigs;
	public Panel monitorPanel;
	public Receiver receiver;
	//private Transmitter transmitter;
	/***************/
	
    private SerialParameters parameters;
    public OutputStream os;
    public InputStream is;
	private String rcvMessage = "", msgBuffer = "";
	
    //private KeyHandler keyHandler;

    private CommPortIdentifier portId;
    public SerialPort serialPort;

	private Thread rcvThread = null;
	private boolean rcvEnable = true;
    public boolean isopen;

    /**
    Creates a SerialConnection object and initilizes variables passed in
    as params.

    @param parent A SerialPortManager object.
    @param parameters A SerialParameters object.
    @param messageAreaOut The TextArea that messages that are to be sent out
    of the serial port are entered into.
    @param messageAreaIn The TextArea that messages comming into the serial
    port are displayed on.
    */
    public SerialConnection(SerialPortManager parent,
							SerialParameters parameters/*,
							TextArea messageAreaOut,
							TextArea messageAreaIn*/) {
		super();
		this.parent = parent;
		this.parameters = parameters;
		this.setLayout(new BorderLayout());
		//this.messageAreaOut = messageAreaOut;
		//this.messageAreaIn = messageAreaIn;
		isopen = false;
   }

   /**
   Attempts to open a serial connection and streams using the parameters
   in the SerialParameters object. If it is unsuccesfull at any step it
   returns the port to a closed state, throws a 
   <code>SerialConnectionException</code>, and returns.
   */
   public void openConnection() throws SerialConnectionException {

		// Obtain a CommPortIdentifier object for the port you want to open.
		try {
			portId = CommPortIdentifier.getPortIdentifier(parameters.getPortName());
		} catch (NoSuchPortException e) {
			throw new SerialConnectionException(e.getMessage());
		}
		
		// Open the port represented by the CommPortIdentifier object. Give
		// the open call a relatively long timeout of 30 seconds to allow
		// a different application to reliquish the port if the user 
		// wants to.
		try {
			serialPort = (SerialPort)portId.open("SerialPortManager", 30000);
		} catch (PortInUseException e) {
			throw new SerialConnectionException(e.getMessage());
		}

		// Set the parameters of the connection. If they won't set, close the
		// port before throwing an exception.
		try {
			setConnectionParameters();
		} catch (SerialConnectionException e) {	
			serialPort.close();
			throw e;
		}

		// Open the input and output streams for the connection. If they won't
		// open, close the port before throwing an exception.
		try {
			os = serialPort.getOutputStream();
			is = serialPort.getInputStream();
		} catch (IOException e) {
			serialPort.close();
			throw new SerialConnectionException("Error opening i/o streams");
		}

		/* Create a new KeyHandler to respond to key strokes in the 
		// messageAreaOut. Add the KeyHandler as a keyListener to the 
		// messageAreaOut.
		keyHandler = new KeyHandler(os);
		messageAreaOut.addKeyListener(keyHandler);
		*/
		
		// Add this object as an event listener for the serial port.
		try {
			serialPort.addEventListener(this);
			///System.out.println("add listener ready");
		} catch (TooManyListenersException e) {
			serialPort.close();
			throw new SerialConnectionException("too many listeners added");
		}

		// Set notifyOnDataAvailable to true to allow event driven input.
		serialPort.notifyOnDataAvailable(true);

		// Set notifyOnBreakInterrup to allow event driven break handling.
		serialPort.notifyOnBreakInterrupt(true);
		
		serialPort.notifyOnCTS(true);
		serialPort.notifyOnDSR(true);
		serialPort.notifyOnRingIndicator(true);
		serialPort.notifyOnCarrierDetect(true);
		serialPort.notifyOnOverrunError(true);
		serialPort.notifyOnParityError(true);
		serialPort.notifyOnFramingError(true);
		serialPort.notifyOnOutputEmpty(true);

		/* Set receive timeout to allow breaking out of polling loop during
		// input handling.
		try {
			serialPort.enableReceiveTimeout(30);
		} catch (UnsupportedCommOperationException e) {
		}*/
		
		// Add ownership listener to allow ownership event handling.
		portId.addPortOwnershipListener(this);
		this.createPanel();
		/*Start revThread*/
		if(rcvEnable && rcvThread == null){
			///System.out.println("thread start");
			rcvThread = new Thread(this.receiver, "Rcv " + serialPort.getName());
			rcvThread.start();
		}
		this.showValues();
		isopen = true;
    }//openConnection end
	
	/*创建监视面板*/
	public void createPanel(){
		if(isopen == true){
			ctlSigs.setPort(this.serialPort);
		}else{
			monitorPanel = new Panel();
			monitorPanel.setLayout(new BorderLayout());
			receiver = new Receiver(this);
			monitorPanel.add("East", receiver);
			this.add("Center", monitorPanel);
			
			ctlSigs = new CtlSigDisplay(this.serialPort);
			this.add("North", ctlSigs);
			
			parent.addPanel(this);
		}
	}
	/*显示监视面板数据*/
	public void showValues(){
		ctlSigs.showValues();
		ctlSigs.showErrorValues();
	}
	/*复位监视面板*/
	public void clearValues(){
		ctlSigs.clearValues();
		ctlSigs.clearErrorValues();
	}

    /**
    Sets the connection parameters to the setting in the parameters object.
    If set fails return the parameters object to origional settings and
    throw exception.
    */
    public void setConnectionParameters() throws SerialConnectionException {

		// Save state of parameters before trying a set.
		int oldBaudRate = serialPort.getBaudRate();
		int oldDatabits = serialPort.getDataBits();
		int oldStopbits = serialPort.getStopBits();
		int oldParity   = serialPort.getParity();
		int oldFlowControl = serialPort.getFlowControlMode();

		// Set connection parameters, if set fails return parameters object
		// to original state.
		try {
			serialPort.setSerialPortParams(parameters.getBaudRate(),
						  parameters.getDatabits(),
						  parameters.getStopbits(),
						  parameters.getParity());
		} catch (UnsupportedCommOperationException e) {
			parameters.setBaudRate(oldBaudRate);
			parameters.setDatabits(oldDatabits);
			parameters.setStopbits(oldStopbits);
			parameters.setParity(oldParity);
			throw new SerialConnectionException("Unsupported parameter");
		}

		// Set flow control.
		try {
			serialPort.setFlowControlMode(parameters.getFlowControlIn() 
						   | parameters.getFlowControlOut());
		} catch (UnsupportedCommOperationException e) {
			throw new SerialConnectionException("Unsupported flow control");
		}
    }//setConnectionParameters end

    /**
    Close the port and clean up associated elements.
    */
    public void closeConnection() {
		// If port is alread closed just return.
		if (!isopen) {
			return;
		}

		// Remove the key listener.
		//messageAreaOut.removeKeyListener(keyHandler);

		// Check to make sure serialPort has reference to avoid a NPE.
		if (serialPort != null) {
			try {
				// close the i/o streams.
				os.close();
				is.close();
			} catch (IOException e) {
				System.err.println(e);
			}
			// Close the port.
			serialPort.close();
			// Remove the ownership listener.
			portId.removePortOwnershipListener(this);
			if(rcvThread != null){
				this.rcvThread.interrupt();
				this.rcvThread = null;
			}
		}
		isopen = false;
    }//closeConnection end

    /**
    Send a one second break signal.
    */
    public void sendBreak() {
		serialPort.sendBreak(1000);
    }

    /**
    Reports the open status of the port.
    @return true if port is open, false if port is closed.
    */
    public boolean isOpen() {
		return isopen;
    }

    /**
    Handles SerialPortEvents. 监视串口运行状态
    */
    public void serialEvent(SerialPortEvent e) {
		// Determine type of event.
		switch (e.getEventType()) {				
			case SerialPortEvent.OE:
				this.ctlSigs.OELabel.setState(e.getNewValue());
				break;
			case SerialPortEvent.FE:
				this.ctlSigs.FELabel.setState(e.getNewValue());
				break;
			case SerialPortEvent.PE:
				this.ctlSigs.PELabel.setState(e.getNewValue());
				break;
			case SerialPortEvent.CD:
			case SerialPortEvent.CTS:
			case SerialPortEvent.DSR:
			case SerialPortEvent.RI:
				this.ctlSigs.showValues();
				break;
			case SerialPortEvent.DATA_AVAILABLE:
                            if(parent.ONFILE){
                                break;
                            }
				this.ctlSigs.DA = true;
				this.ctlSigs.showErrorValues();
				if(rcvThread != null){
					synchronized(receiver){
						receiver.notify();
                                                System.out.println("rcv thread notified!!!");
					}
				}else if(rcvEnable){
					System.out.println(serialPort.getName() + "Receive thread has died!");
					rcvThread = new Thread(this.receiver, "Rcv " + serialPort.getName());
					rcvThread.start();
				}else{
					retrieveMessage(this.receiver.readData());
				}
				break;
			// If break event append BREAK RECEIVED message.
			case SerialPortEvent.BI:
				this.ctlSigs.BILabel.setState(e.getNewValue());
				break;
				//messageAreaIn.append("\n--- BREAK RECEIVED ---\n");
			case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
				this.ctlSigs.BE = true;
				this.ctlSigs.showErrorValues();
				break;
		}//switch end
    }//serialEvent end
	
	public void retrieveMessage(String msg){
		msgBuffer += msg;
                parent.DA = true;// SerialPortManager DA
                System.out.println("去你MD: " + msgBuffer);
	}
	
	public String GetInputMessage(){
                if(!msgBuffer.endsWith("")){
                    System.out.println("SC: " + msgBuffer);
                }
		rcvMessage = msgBuffer;
		msgBuffer = "";
		return rcvMessage;
	}
	
	public void SendOutputMessage(byte[] msg) throws IOException{
		os.write(msg);
		os.flush();
	}

        public InputStream getIS(){
            return this.is;
        }

        public OutputStream getOS(){
            return this.os;
        }
	
    /**
    Handles ownership events. If a PORT_OWNERSHIP_REQUESTED event is
    received a dialog box is created asking the user if they are 
    willing to give up the port. No action is taken on other types
    of ownership events.
    */
    public void ownershipChange(int type) {
		if (type == CommPortOwnershipListener.PORT_OWNERSHIP_REQUESTED) {
			PortRequestedDialog prd = new PortRequestedDialog(parent);
		}
    }

    /**
    A class to handle <code>KeyEvent</code>s generated by the messageAreaOut.
    When a <code>KeyEvent</code> occurs the <code>char</code> that is 
    generated by the event is read, converted to an <code>int</code> and 
    writen to the <code>OutputStream</code> for the port.
    
    class KeyHandler extends KeyAdapter {
		OutputStream os;

		/**
		Creates the KeyHandler.
		@param os The OutputStream for the port.
		
		public KeyHandler(OutputStream os) {
			super();
			this.os = os;
		}

		/**
		Handles the KeyEvent.
		Gets the <code>char</char> generated by the <code>KeyEvent</code>,
		converts it to an <code>int</code>, writes it to the <code>
		OutputStream</code> for the port.
		
        public void keyTyped(KeyEvent evt) {
			char newCharacter = evt.getKeyChar();
			try {
				os.write((int)newCharacter);
			} catch (IOException e) {
				System.err.println("OutputStream write error: " + e);
			}
        }
    }*/
}
