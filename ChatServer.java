package Pino;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static Set<ClientHandler> clientHandlers = ConcurrentHashMap.newKeySet();
    private static Map<String, ClientHandler> clientsByName = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server in ascolto sulla porta " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Comunicazione con ogni client
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Nome del client
                out.println("Inserisci il tuo nome:");
                clientName = in.readLine();

                // Controllo se il nome è già in uso
                synchronized (clientsByName) {
                    if (clientsByName.containsKey(clientName)) {
                        out.println("Nome già in uso. Disconnettiti e prova con un altro nome.");
                        socket.close();
                        return;
                    }
                    clientsByName.put(clientName, this);
                }

                out.println("Benvenuto " + clientName + "! Puoi iniziare a chattare. Ricorda che usando il formato @username messaggio, puoi inviare messaggi privati.");
                clientHandlers.add(this);
                broadcastMessage(clientName + " si è unito alla chat.");

                // Messaggi del client
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("exit")) {
                        break;
                    }

                    if (message.startsWith("@")) {
                        String[] splitMessage = message.split(" ", 2);
                        if (splitMessage.length > 1) {
                            String recipientName = splitMessage[0].substring(1); // Nome del destinatario
                            String privateMessage = splitMessage[1]; // Contenuto del messaggio
                            sendPrivateMessage(recipientName, privateMessage);
                        } else {
                            out.println("Formato del messaggio privato non valido. Usa: @nomeutente messaggio");
                        }
                    } else {
                        broadcastMessage(clientName + ": " + message); // Messaggio pubblico
                    }

                }

                // Rimuove il client quando esce
                clientHandlers.remove(this);
                clientsByName.remove(clientName);
                socket.close();
                broadcastMessage(clientName + " ha lasciato la chat.");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Messaggi a tutti i client
        private void broadcastMessage(String message) {
            for (ClientHandler client : clientHandlers) {
                client.out.println(message);
            }
        }

        // Metodo per inviare un messaggio privato
        private void sendPrivateMessage(String recipientName, String message) {
            ClientHandler recipient = clientsByName.get(recipientName);
            if (recipient != null) {
                recipient.out.println("[Privato da " + clientName + "]: " + message);
                out.println("[Privato a " + recipientName + "]: " + message);
            } else {
                out.println("Utente " + recipientName + " non trovato.");
            }
        }
    }
}
