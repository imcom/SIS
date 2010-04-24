/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package MyController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 *
 * @author Jinyu
 */
public class SocketInvoker {
    public SocketInvoker(){
    }
    
    public static final String request(String request) throws UnknownHostException, IOException{
        System.out.println("Socket Invoker Start!");
        Socket socket = new Socket("127.0.0.1", 2202);

        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        PrintWriter writer = new PrintWriter(outputStream,true);
        writer.println(request);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String response = "";
        char[] cbuf = new char[1024];
        int inBytes;
        do{
            inBytes = reader.read(cbuf);
            //if(inBytes != -1)
            if(inBytes != -1)
                response += new String(cbuf, 0, inBytes);
        }while(inBytes != -1);
        socket.close();
        return response;
    }
}
