import typedata.Type;
import util.DataOutputStreamFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class RunnableServer implements Runnable {
    protected Socket sock;
    private String name = null;
    protected static HashMap<Socket, Integer> clients = new HashMap<>();
    private static ReentrantLock lockForClientsConcurrency = new ReentrantLock();
    public static void removeClientInClients(Socket socket) {
        lockForClientsConcurrency.lock();
        try {
            clients.remove(socket);
        }finally {
            lockForClientsConcurrency.unlock();
        }
    }
    public static void addClientAndSetRecieveNumInClients(Socket socket) {
        lockForClientsConcurrency.lock();
        int initialRecieveNum = 0;
        try {
            clients.put(socket, initialRecieveNum);
        }
        finally {
            lockForClientsConcurrency.unlock();
        }
    }
    private ThreadLocal<Integer> threadLocalClientSendMessageNum = new ThreadLocal<>();

    public static  void clientReceiveMessageNumPlus1(Socket socket) {
        clients.put(socket, clients.getOrDefault(socket, 0) + 1);
    }

    RunnableServer(Socket socket) {
        this.sock = socket;
    }

    @Override
    public void run() {
        RunnableServerService serverService = new RunnableServerService();
        threadLocalClientSendMessageNum.set(0);
        InputStream fromClient;
        DataInputStream dataInputStream;
        DataOutputStreamFactory dataOutputStreamFactory = new DataOutputStreamFactory();
        try {
            System.out.println(sock + ": is connected");
            while (true) {
                fromClient = sock.getInputStream();
                dataInputStream = new DataInputStream(fromClient);
                byte[] header = serverService.recieveMessageHeaderFromClient(dataInputStream);
                byte[] messageBodyBytes = serverService.receiveMessageBodyFromClient(dataInputStream, header);
                Type messageType = serverService.readType(header);
                Type serverMessageType;
                switch (messageType) {
                    case RESISTERNAME:
                        this.name = serverService.resisterName(messageBodyBytes);
                        break;
                    case MESSAGETOSERVER:
                        threadLocalClientSendMessageNum.set(threadLocalClientSendMessageNum.get()+1);
                        byte[] stringMessageJsonBytes
                            = serverService.implementStringMessageJsonBytes(messageBodyBytes, this.name);
                        byte[] stringMessageServerHeader
                            = serverService.implementStringMessageServerHeaderBytes(stringMessageJsonBytes);
                        serverMessageType = serverService.checkMessageType(stringMessageServerHeader);
                        serverService.treatReceiveNumPlus(serverMessageType, clients, sock, lockForClientsConcurrency);//
                        serverService.broadcastAllUser(serverMessageType, clients, dataOutputStreamFactory
                            , sock, stringMessageJsonBytes, stringMessageServerHeader, lockForClientsConcurrency);
                        break;
                    case IMAGETOSERVER:
                        threadLocalClientSendMessageNum.set(threadLocalClientSendMessageNum.get()+1);
                        byte[] imageMessageJsonBytes
                            = serverService.implementImageMessageJsonBytes(messageBodyBytes,this.name);
                        byte[] imageMessageServerHeader =
                            serverService.implementImageMessageServerHeaderBytes(imageMessageJsonBytes);
                        serverMessageType = serverService.checkMessageType(imageMessageServerHeader);
                        serverService.broadcastAllUser(serverMessageType, clients, dataOutputStreamFactory
                            , sock, imageMessageJsonBytes, imageMessageServerHeader, lockForClientsConcurrency);
                        serverService.treatReceiveNumPlus(serverMessageType, clients, sock, lockForClientsConcurrency);//
                        break;
                }
            }
        } catch (IOException ex) {
            System.out.println(sock + ": error(" + ex + ")");
            System.out.println(name+"is out. ");
        } finally {
            try {
                int clientRecieveMessageNum = clients.get(sock);
                removeClientInClients(sock);
                byte[] closeMessageJsonBytes
                    = serverService.implementCloseBody
                    (this.name, threadLocalClientSendMessageNum.get(), clientRecieveMessageNum);
                byte[] closeMessageServerHeader = serverService.implementCloseHeader(closeMessageJsonBytes);
                Type serverMessageType = serverService.checkMessageType(closeMessageServerHeader);
                serverService.broadcastAllUser(serverMessageType, clients, dataOutputStreamFactory,
                    sock, closeMessageJsonBytes, closeMessageServerHeader, lockForClientsConcurrency);
                serverService.treatReceiveNumPlus(serverMessageType, clients, sock, lockForClientsConcurrency);
            } catch (IOException ex) {
            }
        }
    }
}