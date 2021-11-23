package server;

import server.model.Canal;
import server.model.State;
import server.model.User;
import server.stream.ClientThread;
import server.stream.ServerThread;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * S'occuper de gérer les différentes connexions et permet la communication entre les différents threads.
 * */
public class Server {
    private final ServerThread serverThread;
    private final List<ClientThread> clientThreadList;
    private final List<Canal> canalList;
    private Boolean isRunning;
    private String historicFolder;

    public Server(Integer port) throws IOException {
        this.serverThread = new ServerThread(this, port);
        this.clientThreadList = new ArrayList<>();
        this.canalList = new ArrayList<>();
        this.isRunning = Boolean.FALSE;
        this.historicFolder = "app/historic/";
        this.canalList.add(new Canal("science", "Forum basé sur la science"));
        this.canalList.add(new Canal("nature", "Forum basé sur la nature"));
        this.canalList.add(new Canal("espace", "Forum basé sur l'espace"));
        this.canalList.add(new Canal("technologie", "Forum basé sur la technologie"));
        this.canalList.add(new Canal("people", "Forum basé sur les peoples"));
    }

    public void start() {
        if (this.isRunning) {
            System.err.println("Server already running");
        }
        else {
            this.serverThread.start();
        }
    }

    public void onClientConnected(ClientThread clientThread) {
        this.clientThreadList.add(clientThread);
    }

    public void onClientDisconnected(ClientThread clientThread) { //TODO close thread
        this.clientThreadList.remove(clientThread);
    }

    public void onClientRequest(ClientThread clientThread, String request) {
        String[] args = request.split(" ");
        String cmd = args[0];
        switch (cmd) {
            case "IDENTIFIER":
                try {
                    String login = args[1];
                    String password = args[2];
                    this.authenticate(clientThread, login, password);
                } catch (Exception e) {
                    clientThread.reply("-ERR_SYNTAXE");
                }
                break;
            case "QUITTER":
                this.quit(clientThread);
                break;
            case "LISTER":
                this.list(clientThread);
                break;
            case "REJOINDRE":
                try {
                    String canalName = args[1];
                    this.join(clientThread, canalName);
                }
                catch (Exception e) {
                    clientThread.reply("-ERR_SYNTAXE");
                }
                break;
            case "SORTIR":
                this.leave(clientThread);
                break;
            case "MESSAGE":
                try {
                    String message = request.substring(request.indexOf(" "));
                    this.message(clientThread, message);
                }
                catch (Exception e) {
                    clientThread.reply("-ERR_SYNTAXE");
                }
                break;
            case "MEMBRES":
                this.members(clientThread);
                break;
            case "CONNEXION":
                try {
                    String login = args[1];
                    this.connect(clientThread, login);
                }
                catch (Exception e) {
                    clientThread.reply("-ERR_SYNTAXE");
                }
                break;
            case "DECONNEXION": {
                this.disconnect(clientThread);
                break;
            }
            case "HISTORIQUE": {
                this.historic(clientThread);
                break;
            }
            default:
                clientThread.reply("-ERR_CMDINCONNUE");
        }
    }

    private void historic(ClientThread clientThread) {
        if (clientThread.getUser().getState() == State.AUTHENTICATED) {
            clientThread.reply("-KO_HISTORIQUE: " + "Utilisateur non connecté à un cannal ou à un utilisateur");
        }
        else if (clientThread.getUser().getState() == State.UNAUTHENTICATED) {
            clientThread.reply("-KO_HISTORIQUE: " + "Utilisateur non connecté");
        }
        else if (clientThread.getUser().getState() == State.CONNECTED_CANAL) {
            File file = new File(this.historicFolder + clientThread.getUser().getCanal().getName());
            if (file.exists()) {
                try {
                    BufferedReader buf = new BufferedReader(new FileReader(file));
                    String line;
                    clientThread.reply("+OK_HISTORIQUE");
                    while ((line = buf.readLine()) != null) {
                        clientThread.reply(line);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (clientThread.getUser().getState() == State.CONNECTED_DIRECT) {

        }
    }

    private void authenticate(ClientThread clientThread, String login, String password) {
        if (clientThread.getUser().getState() == State.UNAUTHENTICATED) {
            User user = new User(login, password);
            clientThread.authenticateUser(user);
            clientThread.reply("+OK_IDENTIFIER: " + user.getLogin() + " " + user.getRights());
        }
        else {
            clientThread.reply("-KO_IDENTIFIER: " + "Utilisateur déjà connecté");
        }
    }

    private void quit(ClientThread clientThread) {
        if (clientThread.getUser().getState() == State.AUTHENTICATED) {
            clientThread.quit();
            clientThread.reply("+OK_QUITTER");
        }
        else {
            clientThread.reply("-KO_QUITTER: " + "Utilisateur non connecté");
        }

    }

    private void list(ClientThread clientThread) {
        if (clientThread.getUser().getState() == State.AUTHENTICATED) {
            String reply = "+OK_LISTER: ";
            for (Canal canal : this.canalList) {
                reply += canal.getName() + "(" + canal.getTopic() + "), ";
            }
            reply = reply.substring(0, reply.length() - 2);
            clientThread.reply(reply);
        }
        else {
            clientThread.reply("-KO_LISTER: " + "Utilisateur non connecté");
        }
    }

    private void join(ClientThread clientThread, String canalName) {
        if (clientThread.getUser().getState() == State.AUTHENTICATED) {
            for (Canal canal : this.canalList) {
                if (canal.getName().equals(canalName)) {
                    clientThread.join(canal);
                    User user = clientThread.getUser();
                    canal.addUser(user);
                    clientThread.reply("+OK_REJOINDRE: " + user.getCanal().getName());
                    return;
                }
            }
            clientThread.reply("-KO_REJOINDRE: " + "Canal innexistant");
        }
        else {
            clientThread.reply("-KO_REJOINDRE: " + "Utilisateur non connecté");
        }
    }

    private void leave(ClientThread clientThread) {
        if (clientThread.getUser().getState() == State.CONNECTED_CANAL) {
            User user = clientThread.getUser();
            String name = user.getCanal().getName();
            clientThread.leave();

            for (Canal canal : this.canalList) {
                if (canal.getName().equals(name)) {
                    canal.removeUser(user);
                    clientThread.reply("+OK_SORTIR: " + name);
                    return;
                }
            }
        }
        else {
            clientThread.reply("-KO_REJOINDRE: " + "Utilisateur non connecté");
        }
    }

    private void message(ClientThread clientThread, String message) {
        if (clientThread.getUser().getState() == State.AUTHENTICATED) {
            clientThread.reply("-KO_MESSAGE: " + "Utilisateur non connecté à un cannal ou à un utilisateur");
        }
        else if (clientThread.getUser().getState() == State.UNAUTHENTICATED) {
            clientThread.reply("-KO_MESSAGE: " + "Utilisateur non connecté");
        }
        else if (clientThread.getUser().getState() == State.CONNECTED_CANAL) {
            User user = clientThread.getUser();
            String canalName = user.getCanal().getName();
            for (Canal canal : this.canalList) {
                if (canal.getName().equals(canalName)) {
                    for (User other : canal.getUserList()) {
                        for (ClientThread ct : clientThreadList) {
                            if (!ct.getUser().equals(other) && ct.getUser().getCanal().getName() == canalName) {
                                ct.message("NOTIFIER: CANNAL " + canalName + " PARLE: " + user.getLogin() + " << " + message + " >>");
                            }
                        }
                    }
                }
            }
            clientThread.reply("+OK_MESSAGE: CANNAL " + canalName + " PARLE: " + user.getLogin() + " << " + message + " >>");
            this.saveCanalMessage(user.getLogin(), canalName, message);
        }
        else if (clientThread.getUser().getState() == State.CONNECTED_DIRECT) {
            ClientThread ct =  clientThread.getDirectMessage();
            String other =  clientThread.getDirectMessage().getUser().getLogin();
            String name = clientThread.getUser().getLogin();
            ct.message("NOTIFIER: DM " + other + " PARLE: " + name + " << " + message + " >>");
            clientThread.reply("+OK_MESSAGE: DM " + other + " PARLE: " + name + " << " + message + " >>");
            this.saveMessage(name, other, message);
        }
    }

    private void members(ClientThread clientThread) {
        if (clientThread.getUser().getState() == State.AUTHENTICATED) {
            String reply = "";
            for (ClientThread ct : this.clientThreadList) {
                reply += ct.getUser().getLogin() + ", ";
            }
            reply = reply.substring(0, reply.length() - 2);
            clientThread.reply("+OK_MEMBRES: " + reply);
        }
        else if (clientThread.getUser().getState() == State.UNAUTHENTICATED) {
            clientThread.reply("-KO_MESSAGE: " + "Utilisateur non connecté");
        }
        else if (clientThread.getUser().getState() == State.CONNECTED_CANAL) {
            User user = clientThread.getUser();
            String canalName = user.getCanal().getName();

            for (Canal canal : this.canalList) {
                if (canal.getName().equals(canalName)) {
                    String reply = "";
                    for (User other : canal.getUserList()) {
                        reply += other.getLogin() + ", ";
                    }
                    reply = reply.substring(0, reply.length() - 2);
                    clientThread.reply("+OK_MEMBRES: " + reply);
                }
            }
        }
        else if (clientThread.getUser().getState() == State.CONNECTED_DIRECT) {
            String login1 = clientThread.getUser().getLogin();
            String login2 = clientThread.getDirectMessage().getUser().getLogin();
            clientThread.reply("+OK_MEMBRES: " + login1 + ", " + login2);
        }
    }

    private void connect(ClientThread clientThread, String login) {
        if (clientThread.getUser().getState() == State.AUTHENTICATED) {
            ClientThread other = this.getClientThread(login);
            if (other != null) {
                clientThread.connect(other);
                clientThread.message("+OK_CONNEXION: " + login);
                String name = clientThread.getUser().getLogin();
                other.message("+OK_CONNEXION: " + name);
            }
        }
        else if (clientThread.getUser().getState() == State.UNAUTHENTICATED) {
            clientThread.reply("-KO_CONNEXION: " + "Utilisateur non connecté");
        }
        else if (clientThread.getUser().getState() == State.CONNECTED_CANAL) {
            clientThread.reply("-KO_CONNEXION: " + "Utilisateur non connecté à un cannal");
        }
        else if (clientThread.getUser().getState() == State.CONNECTED_DIRECT) {
            clientThread.reply("-KO_CONNEXION: " + "Utilisateur déjà connecté à un autre utilisateur");
        }
    }

    private void disconnect(ClientThread clientThread) {
        if (clientThread.getUser().getState() != State.CONNECTED_DIRECT) {
            clientThread.reply("-KO_DECONNEXION: " + "Utilisateur non connecté à un autre utilisateur");
        }
        else {
            clientThread.disconnect();
        }
    }

	public User getUser(String login) {
		for (ClientThread ct : this.clientThreadList) {
			if (ct.getUser().getLogin().equals(login)) {
				return ct.getUser();
			}
		}
		return null;
	}

	public ClientThread getClientThread(String login) {
		for (ClientThread ct : this.clientThreadList) {
			if (ct.getUser().getLogin().equals(login)) {
				return ct;
			}
		}
		return null;
	}

    private void saveMessage(String name, String other, String message) {
        String fileName = name.compareTo(other) <= 0 ? name + "-" + other : other + "-" + name;
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(new FileWriter(this.historicFolder + fileName, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
        printWriter.println(name + " -> " + other + " : " + message);
        printWriter.close();
    }

    private void saveCanalMessage(String name, String canal, String message) {
        String fileName = canal;
        PrintWriter printWriter = null;
        try {
            printWriter = new PrintWriter(new FileWriter(this.historicFolder + fileName, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
        printWriter.println(name + " : " + message);
        printWriter.close();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java EchoServer <port number>");
            System.exit(1);
        }

        int portNumber = Integer.parseInt(args[0]);
        Server server = new Server(portNumber);
        server.start();
    }
}
