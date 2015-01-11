package com.sun.tools.example.debug.tty;

import java.io.*;
import java.net.*;

interface Service {
    void doService(Socket client) throws IOException;
}

class Server {
    private ServerSocket server;
    private Service service;
    
    Server(int port, Service service)  throws IOException {   
        int i = 0;
        int serverPort = 0;
        for (i = 0; i < 10; i++) {
            int testPort = port + i;
            if (portIsOpen(testPort)) {
                serverPort = testPort;
                break;
            }
        }    

        if (i >= 10) {
            this.service= null;
            return;
        }
        
        this.server = new ServerSocket(serverPort);        
        this.service = service;
    }

    boolean portIsOpen(int port) {
        ServerSocket testServer;
        try {
            testServer = new ServerSocket(port);
            testServer.close();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
    int getPort() {
        if (server != null && !server.isClosed())
            return  server.getLocalPort();
        else
            return -1;
    }
    
    void start() {
        if (service == null) {
            System.out.println("Server can't start !!");
            return;
        }
        
        System.out.println("Server listening on port: " + server.getLocalPort());
        Thread thd = new Thread(new Runnable(){
            public void run() {
                try{                        
                    while (true) {                                    
                        Socket client = server.accept();
                        service.doService(client);                                 
                    }
                } catch (Exception ex) {
                    // do nothing
                }
            }
        });
        thd.start();
    }
}