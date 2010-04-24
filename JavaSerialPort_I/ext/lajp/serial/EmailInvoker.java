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
public class EmailInvoker {
    public EmailInvoker(){
    }
    
    public static final String rcvMail(String args) throws UnknownHostException, IOException{
        System.out.println("Email Invoker Start!");
        Socket socket = new Socket("127.0.0.1", 2200);

        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        PrintWriter writer = new PrintWriter(outputStream,true);
        writer.println(args);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String response = reader.readLine();
        socket.close();
        return response;
    }

    public static final String sendMail(String args) throws UnknownHostException, IOException{
        System.out.println("Email Invoker Start!");
        Socket socket = new Socket("127.0.0.1", 2218);

        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        PrintWriter writer = new PrintWriter(outputStream,true);
        writer.println(args);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String response = reader.readLine();
        socket.close();
        return response;
    }
}
