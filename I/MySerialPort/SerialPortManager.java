/*
 *	SerialPortManager用于处理串口的初始化；
 *	提供串口参数设置，监视串口数据流；
 *	使用javax.comm库实现；
 *
 */

package MySerialPort;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.comm.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.Enumeration;
import java.awt.Color;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 串口管理GUI
 * @author Jinyu
 */
public class SerialPortManager extends Frame implements ActionListener {
	
	/*窗口界面大小*/
	final int HEIGHT = 550;
	final int WIDTH = 510;
	
	/*操作面板声明*/
	private Button openButton;
    private Button closeButton;
    private Button breakButton;
    private Panel buttonPanel;
	
	/*读写文件菜单*/
	private MenuBar menuBar;
    private Menu fileMenu;
    private MenuItem openItem;
    private MenuItem saveItem;
    private MenuItem exitItem;
	
	/*数据追踪面板
	private Panel    messagePanel;
    private TextArea messageAreaOut;
    private TextArea messageAreaIn;
	*/
	
	/*参数设置面板*/
	private ConfigurationPanel configurationPanel;
	
	//private CtlSigDisplay ctlSigs;
	
	/*串口管理对象*/
    private SerialParameters parameters;
    private SerialConnection connection;
	//private Panel monitorPanel;
	
	/*参数变量*/
	private Properties props = null;
	
	//private Object lock;
	//private boolean portAvailable = true;
	public boolean isOpen = false;
	public boolean DA = false;
        public boolean ONFILE = false;
	
	/*主函数入口，创建控制窗口
	public static void main(String[] args){
		if ((args.length > 0)
			&& (args[0].equals("-h")
			|| args[0].equals("-help"))) {
			System.out.println("Usage: java SerialPortManager [configuration File]");
			System.exit(1);
		}
		
		SerialPortManager serialManager = new SerialPortManager(args);
		serialManager.setVisible(true);
		serialManager.setBackground(Color.gray);
		serialManager.repaint();
	}
	*/
	/*构造函数，初始化控制窗口
	 *参数 args 用于指定初始化使使用的配置文件
	*/
	public SerialPortManager(String[] args){
		super("SerialPortManager Manager");
		
		parameters = new SerialParameters();
		
		//lock = new Object();
		
		/*设定GUI*/
		addWindowListener(new CloseHandler(this));
		
		/*设定文件操作菜单*/
		menuBar = new MenuBar();

		fileMenu = new Menu("File");

		openItem = new MenuItem("Load");
		openItem.addActionListener(this);
		fileMenu.add(openItem);

		saveItem = new MenuItem("Save");
		saveItem.addActionListener(this);
		fileMenu.add(saveItem);

		exitItem = new MenuItem("Exit");
		exitItem.addActionListener(this);
		fileMenu.add(exitItem);
		
		menuBar.add(fileMenu);

		setMenuBar(menuBar);
		
		/*设定消息监视面板
		messagePanel = new Panel();
		messagePanel.setLayout(new GridLayout(2, 1));

		messageAreaOut = new TextArea();
		messageAreaOut.setEditable(false);
		messagePanel.add(messageAreaOut);

		messageAreaIn = new TextArea();
		messageAreaIn.setEditable(false);
		messagePanel.add(messageAreaIn);
		*/
		
		/*设定连接监控面板位置*/
		
		//add(connectionPanel, "Center");
		
		configurationPanel = new ConfigurationPanel(this);
		
		//ctlSigs = new CtlSigDisplay();

		/*设定控制按钮面板*/
		buttonPanel = new Panel();

		openButton = new Button("Open Port");
		openButton.addActionListener(this);
		buttonPanel.add(openButton);

		closeButton = new Button("Close Port");
		closeButton.addActionListener(this);
		closeButton.setEnabled(false);
		buttonPanel.add(closeButton);

		breakButton = new Button("Send Break");
		breakButton.addActionListener(this);
		breakButton.setEnabled(false);
		buttonPanel.add(breakButton);
		
		/*应用布局限制控制面板*/
		Panel managePanel = new Panel();

		GridBagLayout gridBag = new GridBagLayout();
		GridBagConstraints cons = new GridBagConstraints();

		managePanel.setLayout(gridBag);

		cons.gridwidth = GridBagConstraints.REMAINDER;
		gridBag.setConstraints(configurationPanel, cons);
		cons.weightx = 1.0;
		
		managePanel.add(configurationPanel);
		gridBag.setConstraints(buttonPanel, cons);
		managePanel.add(buttonPanel);
		//managePanel.add(ctlSigs);
		/*设定控制面板位置*/
		add(managePanel, "South");
		
		/*解析文件参数*/
		parseArgs(args);

		connection = new SerialConnection(this, parameters/*,
											messageAreaOut, messageAreaIn*/);
		setConfigurationPanel();

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

		setLocation(screenSize.width/2 - WIDTH/2,
					screenSize.height/2 - HEIGHT/2);

		setSize(WIDTH, HEIGHT);
	}//SerialPortManager end
	
	public void addPanel(SerialConnection conn){
		this.add(conn, "Center");
		this.validate();
	}
	
	/*解析配置文件函数，从配置文件中获取串口配置参数
	 *配置文件默认位置为程序运行目录
	*/
	private void parseArgs(String[] args) {
		if (args.length < 1) {
			return;
		}

		File f = new File(args[0]);

		if (!f.exists()) {
			f = new File(System.getProperty("user.dir")
				 + System.getProperty("file.separator")
				 + args[0]);
		}

		if (f.exists()) {
			try {
				FileInputStream fis = new FileInputStream(f);
				props = new Properties();
				props.load(fis);
				fis.close();
				loadParams();
			} catch (IOException e) {
			}
		}
    }
	
	/*根据配置文件中的定义设置参数*/
	private void loadParams() {
		parameters.setPortName(props.getProperty("portName"));
		parameters.setBaudRate(props.getProperty("baudRate"));
		parameters.setFlowControlIn(props.getProperty("flowControlIn"));
		parameters.setFlowControlOut(props.getProperty("flowControlOut"));
		parameters.setParity(props.getProperty("parity"));
		parameters.setDatabits(props.getProperty("databits"));
		parameters.setStopbits(props.getProperty("stopbits"));

		setConfigurationPanel();
    }
	
	/*设置配置面板界面*/
	public void setConfigurationPanel() {
		configurationPanel.setConfigurationPanel();
    }
	
	/*操作相应处理*/
	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();

		// Loads a configuration file.
		if (cmd.equals("Load")) {
			if (connection.isOpen()) {
				AlertDialog ad = new AlertDialog(this, "Port Open!",
												"Configuration may not",
												"be loaded",
												"while a port is open.");
			} else {
				FileDialog fd = new FileDialog(this,
								   "Load Port Configuration",
								   FileDialog.LOAD);
				fd.setVisible(true);
				String file = fd.getFile();
				if (file != null) {
					String dir = fd.getDirectory();
					File f = new File(dir + file);
					try {
						FileInputStream fis = new FileInputStream(f);
						props = new Properties();
						props.load(fis);
						fis.close();
					} catch (FileNotFoundException e1) {
						System.err.println(e1);
					} catch (IOException e2) {
						System.err.println(e2);
					}
					loadParams();
				}//if(file != null) end
			}//connection.isOpen end
		}//command Load end

		// Saves a configuration file.
		if (cmd.equals("Save")) {
			configurationPanel.setParameters();
			FileDialog fd = new FileDialog(this, "Save Port Configuration",
											FileDialog.SAVE);
			fd.setFile("SerialPortManager.properties");
			fd.setVisible(true);
			String fileName = fd.getFile();
			String directory = fd.getDirectory();
			if ((fileName != null) && (directory != null)) {
                try {
                    writeFile(directory + fileName);
                } catch (IOException ex) {
                    Logger.getLogger(SerialPortManager.class.getName()).log(Level.SEVERE, null, ex);
                }
			}
		}//command Save end

		// Calls shutdown, which exits the program.
		if (cmd.equals("Exit")) {
			shutdown();
		}

		// Opens a port.
		if (cmd.equals("Open Port")) {
			openButton.setEnabled(false);
			Cursor previousCursor = getCursor();
			setNewCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			configurationPanel.setParameters();
			try {
				connection.openConnection();
			} catch (SerialConnectionException e2) {
				AlertDialog ad = new AlertDialog(this,
												"Error Opening Port!",
												"Error opening port,",
												e2.getMessage() + ".",
												"Select new settings, try again.");
				openButton.setEnabled(true);
				setNewCursor(previousCursor);
				return;
			}
			portOpened();//设置按钮状态至端口开启
			/*显示端口运行状态
			ctlSigs.setPort(connection.GetSerialPort());
			ctlSigs.showValues();
			ctlSigs.showErrorValues();*/
			setNewCursor(previousCursor);
                        isOpen = true;
		}//command Open port end

		// Closes a port.
		if (cmd.equals("Close Port")) {
			/*ctlSigs.clearValues();
			ctlSigs.clearErrorValues();*/
			connection.clearValues();
			portClosed();
		}

		// Sends a break signal to the port.
		if (cmd.equals("Send Break")) {
			connection.sendBreak();
		}
    }//actionPerformed end
	
	private void shutdown() {
                isOpen = false;
		connection.closeConnection();
		System.exit(1);
    }
	
	/*设置按钮状态*/
	public void portOpened() {
		openButton.setEnabled(false);
		closeButton.setEnabled(true);
		breakButton.setEnabled(true);
    }
	
	public void portClosed() {
		connection.closeConnection();
		openButton.setEnabled(true);
		closeButton.setEnabled(false);
		breakButton.setEnabled(false);
    }
	
	/*设置鼠标位置*/
	private void setNewCursor(Cursor c) {
		setCursor(c);
		//messageAreaIn.setCursor(c);
		//messageAreaOut.setCursor(c);
    }
	
	/*将当前的配置写入文件*/
	private void writeFile(String path) throws IOException {

        Properties newProps;
        FileOutputStream fileOut = null;

        newProps = new Properties();

        newProps.put("portName", parameters.getPortName());
        newProps.put("baudRate", parameters.getBaudRateString());
        newProps.put("flowControlIn", parameters.getFlowControlInString());
        newProps.put("flowControlOut", parameters.getFlowControlOutString());
        newProps.put("parity", parameters.getParityString());
        newProps.put("databits", parameters.getDatabitsString());
        newProps.put("stopbits", parameters.getStopbitsString());

        try {
            fileOut = new FileOutputStream(path);
        } catch (IOException e) {
            System.out.println("Could not open file for writiing");
        }

        newProps.store(fileOut, "SerialPortManager poperties");

        try {
            fileOut.close();
        } catch (IOException e) {
            System.out.println("Could not close file for writiing");
        }
    }//writeFile end
	
	/*创建并初始化配置面板，初始参数由 parameters 指定*/
	class ConfigurationPanel extends Panel implements ItemListener {

		private Frame parent;

		private Label portNameLabel;
		private Choice portChoice;

		private Label baudLabel;
		private Choice baudChoice;

		private Label flowControlInLabel;
		private Choice flowChoiceIn;

		private Label flowControlOutLabel;
		private Choice flowChoiceOut;

		private Label databitsLabel;
		private Choice databitsChoice;

		private Label stopbitsLabel;
		private Choice stopbitsChoice;

		private Label parityLabel;
		private Choice parityChoice;

		/**
		Creates and initilizes the configuration panel. The initial settings
		are from the parameters object.
		*/
		public ConfigurationPanel(Frame parent) {
			this.parent = parent;

			setLayout(new GridLayout(4, 4));

			portNameLabel = new Label("Port Name:", Label.LEFT);
			add(portNameLabel);

			portChoice = new Choice();
			portChoice.addItemListener(this);
			add(portChoice);
			listPortChoices();
			portChoice.select(parameters.getPortName());

			baudLabel = new Label("Baud Rate:", Label.LEFT);
			add(baudLabel);

			baudChoice = new Choice();
			baudChoice.addItem("300");
			baudChoice.addItem("2400");
			baudChoice.addItem("9600");
			baudChoice.addItem("14400");
			baudChoice.addItem("28800");
			baudChoice.addItem("38400");
			baudChoice.addItem("57600");
			baudChoice.addItem("152000");
			baudChoice.select(Integer.toString(parameters.getBaudRate()));
			baudChoice.addItemListener(this);
			add(baudChoice);

			flowControlInLabel = new Label("Flow Control In:", Label.LEFT);
			add(flowControlInLabel);

			flowChoiceIn = new Choice();
			flowChoiceIn.addItem("None");
			flowChoiceIn.addItem("Xon/Xoff In");
			flowChoiceIn.addItem("RTS/CTS In");
			flowChoiceIn.select(parameters.getFlowControlInString());
			flowChoiceIn.addItemListener(this);
			add(flowChoiceIn);

			flowControlOutLabel = new Label("Flow Control Out:", Label.LEFT);
			add(flowControlOutLabel);

			flowChoiceOut = new Choice();
			flowChoiceOut.addItem("None");
			flowChoiceOut.addItem("Xon/Xoff Out");
			flowChoiceOut.addItem("RTS/CTS Out");
			flowChoiceOut.select(parameters.getFlowControlOutString());
			flowChoiceOut.addItemListener(this);
			add(flowChoiceOut);

			databitsLabel = new Label("Data Bits:", Label.LEFT);
			add(databitsLabel);

			databitsChoice = new Choice();
			databitsChoice.addItem("5");
			databitsChoice.addItem("6");
			databitsChoice.addItem("7");
			databitsChoice.addItem("8");
			databitsChoice.select(parameters.getDatabitsString());
			databitsChoice.addItemListener(this);
			add(databitsChoice);

			stopbitsLabel = new Label("Stop Bits:", Label.LEFT);
			add(stopbitsLabel);

			stopbitsChoice = new Choice();
			stopbitsChoice.addItem("1");
			stopbitsChoice.addItem("1.5");
			stopbitsChoice.addItem("2");
			stopbitsChoice.select(parameters.getStopbitsString());
			stopbitsChoice.addItemListener(this);
			add(stopbitsChoice);

			parityLabel = new Label("Parity:", Label.LEFT);
			add(parityLabel);

			parityChoice = new Choice();
			parityChoice.addItem("None");
			parityChoice.addItem("Even");
			parityChoice.addItem("Odd");
			parityChoice.select("None");
			parityChoice.select(parameters.getParityString());
			parityChoice.addItemListener(this);
			add(parityChoice);
		}

		/**
		Sets the configuration panel to the settings in the parameters object.
		*/
		public void setConfigurationPanel() {
			portChoice.select(parameters.getPortName());
			baudChoice.select(parameters.getBaudRateString());
			flowChoiceIn.select(parameters.getFlowControlInString());
			flowChoiceOut.select(parameters.getFlowControlOutString());
			databitsChoice.select(parameters.getDatabitsString());
			stopbitsChoice.select(parameters.getStopbitsString());
			parityChoice.select(parameters.getParityString());
		}

		/**
		Sets the parameters object to the settings in the configuration panel.
		*/
		public void setParameters() {
			parameters.setPortName(portChoice.getSelectedItem());
			parameters.setBaudRate(baudChoice.getSelectedItem());
			parameters.setFlowControlIn(flowChoiceIn.getSelectedItem());
			parameters.setFlowControlOut(flowChoiceOut.getSelectedItem());
			parameters.setDatabits(databitsChoice.getSelectedItem());
			parameters.setStopbits(stopbitsChoice.getSelectedItem());
			parameters.setParity(parityChoice.getSelectedItem());
		}

		/**
		Sets the elements for the portChoice from the ports available on the
		system. Uses an emuneration of comm ports returned by
		CommPortIdentifier.getPortIdentifiers(), then sets the current
		choice to a mathing element in the parameters object.
		*/
		void listPortChoices() {
			CommPortIdentifier portId;
			Enumeration en = CommPortIdentifier.getPortIdentifiers();

			// iterate through the ports.
			while (en.hasMoreElements()) {
				portId = (CommPortIdentifier) en.nextElement();
				if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
					portChoice.addItem(portId.getName());
				}
			}
			portChoice.select(parameters.getPortName());
		}

		/**
		Event handler for changes in the current selection of the Choices.
		If a port is open the port can not be changed.
		If the choice is unsupported on the platform then the user will
		be notified and the settings will revert to their pre-selection
		state.
		*/
		public void itemStateChanged(ItemEvent e) {
			/*Check if port is open.*/
			if (connection.isOpen()) {
				/*If port is open do not allow port to change.*/
				if (e.getItemSelectable() == portChoice) {
					/*Alert user.*/
					AlertDialog ad = new AlertDialog(parent, "Port Open!",
													 "Port can not",
													 "be changed",
													 "while a port is open.");

					/*Return configurationPanel to pre-choice settings.*/
					setConfigurationPanel();
					return;
				}//if conf changeable end
				/*Set the parameters from the choice panel.*/
				setParameters();
				try {
					/*Attempt to change the settings on an open port.*/
					connection.setConnectionParameters();
				} catch (SerialConnectionException ex) {
					/*If setting can not be changed, alert user, return to
					pre-choice settings.*/
					AlertDialog ad = new AlertDialog(parent,
													"Unsupported Configuration!",
													"Configuration Parameter unsupported,",
													"select new value.",
													"Returning to previous configuration.");
					setConfigurationPanel();
				}
			} else {
				/*Since port is not open just set the parameter object.*/
				setParameters();
			}
		}//itemStateChanged end
    }//class ConfigurationPanel end
	
	class CloseHandler extends WindowAdapter {
		SerialPortManager sp;

		public CloseHandler(SerialPortManager sp) {
			this.sp = sp;
		}

		public void windowClosing(WindowEvent e) {
			sp.shutdown();
		}
    }
	
	public String getMessage(){
                this.DA = false;
                String ret = connection.GetInputMessage();
                
		return ret;
	}
	
	public void sendMessage(byte[] msg) throws IOException{
		connection.SendOutputMessage(msg);
	}

        public InputStream getIS(){
            return connection.getIS();
        }

        public OutputStream getOS(){
            return connection.getOS();
        }
}