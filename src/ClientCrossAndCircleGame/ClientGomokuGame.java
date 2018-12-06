package ClientCrossAndCircleGame;

/*
 * Ariel Grabijas
 * Java Gomoku game client.
 */
import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientGomokuGame implements Runnable{
    private Socket connection;
    private Scanner userIn = new Scanner(System.in);
    
    public ClientGomokuGame(final String host, final int port){
        try {
            this.connection = new Socket(host, port);
        } catch (IOException ex) {
            Logger.getLogger(ClientGomokuGame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        try {
            new GomokuGame(connection).play();
        } catch (IOException ex) {
            Logger.getLogger(ClientGomokuGame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private enum Protocol{
        INSTANCE(State.START_STATE);
        private static enum State{START_STATE, 
                                  READY_FOR_NEW_TURN, 
                                  WAITING_FOR_MY_TURN, 
                                  MAKING_NEW_MOVE, 
                                  I_WON, 
                                  END_STATE};
        public static enum Input{YOU_ARE_CONNECTED, 
                                 YOUR_BOARD_SYMBOL,
                                 START_THE_GAME, 
                                 WAIT_FOR_YOUR_TURN, 
                                 NEW_MOVE, 
                                 INCORRECT_MOVE, 
                                 NEXT_PLAYER_TURN, 
                                 ANOTHER_PLAYER_COORDINATES, 
                                 YOU_WON, 
                                 YOU_LOST};
        public static enum Output{MY_MOVE};
        private State currentState;
        
        Protocol(State startState){
            this.currentState = startState;
        }
        
        public boolean validateInput(Command command) throws IOException{
        	return processInput(Protocol.Input.valueOf(command.getCommand()));
        }
        
        
        public boolean processWriteCoordinates(Command command) throws IOException{
            return isCoordinateCorrect(command.getAdditionalValues().get(0)) && processOutput();
        }
        
            private boolean processOutput() throws IOException {
                if (currentState == State.MAKING_NEW_MOVE)
                    return true;
                else throw new IOException();
            }
        
            private boolean isCoordinateCorrect(final String input){
                Pattern p = Pattern.compile("[A-J][0-9]");
                Matcher matcher = p.matcher(input);
                return matcher.matches();
            }
                            
            private boolean processInput(final Input input) throws IOException {
                switch (currentState) {
                    case START_STATE:
                        switch (input) {
                            case YOU_ARE_CONNECTED:
                                return true;
                            case START_THE_GAME:
                                currentState = State.READY_FOR_NEW_TURN;
                                return true;
                            case YOUR_BOARD_SYMBOL:
                                return true;
                            default:
                                return false;
                        }
                    case READY_FOR_NEW_TURN:
                        switch (input) {
                            case NEW_MOVE:
                                currentState = State.MAKING_NEW_MOVE;
                                return true;
                            case WAIT_FOR_YOUR_TURN:
                                currentState = State.WAITING_FOR_MY_TURN;
                                return true;
                            default:
                                return false;
                        }
                    case WAITING_FOR_MY_TURN:
                        switch (input) {
                            case NEW_MOVE:
                                currentState = State.MAKING_NEW_MOVE;
                                return true;
                            case ANOTHER_PLAYER_COORDINATES:
                                currentState = State.WAITING_FOR_MY_TURN;
                                return true;
                            case YOU_LOST:
                                currentState = State.END_STATE;
                                return true;
                            default:
                                return false;
                        }
                    case MAKING_NEW_MOVE:
                        switch (input) {
                            case INCORRECT_MOVE:
                                currentState = State.MAKING_NEW_MOVE;
                                return true;
                            case NEXT_PLAYER_TURN:
                                currentState = State.READY_FOR_NEW_TURN;
                                return true;
                            case YOU_WON:
                                currentState = State.END_STATE;
                                return true;
                            default:
                                return false;
                        }
                    default:
                        return false;
                }
            }
    }
    
    private interface Communication{
        public abstract boolean writeCoordinates(final String moveCoordinates) throws IOException;
        public abstract Command readCommand() throws IOException;
        public void close() throws IOException;
    }
    
    private class TcpIpCommunication implements Communication{
        private final Socket connection;
        private final BufferedReader in;
        private final PrintWriter out;
        
        TcpIpCommunication(final Socket connection) throws IOException{
                this.connection = connection;
                this.out = new PrintWriter(connection.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        }
        
        @Override
        public boolean writeCoordinates(final String moveCoordinates) throws IOException{
        	Command command = new Command(String.valueOf(Protocol.Output.MY_MOVE));
        	command.addAdditionalValue(moveCoordinates);
        	Gson gson = new Gson();
        	String jsonCommand = gson.toJson(command);
            if(Protocol.INSTANCE.processWriteCoordinates(command)){
                out.println(jsonCommand);
                return true;
            }
            else return false;
        }
        
        @Override
        public Command readCommand() throws IOException {
            String jsonCommand = this.in.readLine();
            Gson gson = new Gson();
            Command command = gson.fromJson(jsonCommand, Command.class);
            if(Protocol.INSTANCE.validateInput(command))
                return command;
            else 
                throw new IOException();
        }
                
        @Override
        public void close() throws IOException{
            this.connection.close();
            this.in.close();
            this.out.close();
        }
    }
    
    public class Command {
	    @SerializedName("command")
	    @Expose
	    private String command;
	    
	    @SerializedName("additionalValues")
	    @Expose
	    private List<String> additionalValues = new ArrayList<String>();
	
	    public Command(String command) {
	    	this.command = command;
	    }
	    
	    public String getCommand() {
	    	return command;
	    }
	
	    public void setCommand(String command) {
	    	this.command = command;
	    }
	
	    public List<String> getAdditionalValues() {
	    	return additionalValues;
	    }
	
	    public void setAdditionalValues(List<String> additionalValues) {
	    	this.additionalValues = additionalValues;
	    }
	    
	    public void addAdditionalValue(String value) {
			this.additionalValues.add(value);
		}
    }
    
    private class GomokuGame{
        private boolean gameIsNotOver = true;
        private String symbolOnGameBoard;
        private final GameLogic gameLogic;
        private final UserInterface userInterface = new UserInterface();        
        private final Communication communication;
        
        public GomokuGame(Socket connection) throws IOException{
            this.gameLogic = new GameLogic();
            this.communication = new TcpIpCommunication(connection);
        }
        
        public void play(){
            try {
                Command command;
                while(gameIsNotOver){
                    command = communication.readCommand();
                    receiveInitialization(command);
                    runGameLogic(command);
                    validate(command);
                }
            } catch (IOException ex) {
                Logger.getLogger(ClientGomokuGame.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    communication.close();
                    userIn.close();
                } catch (IOException ex) {
                    Logger.getLogger(ClientGomokuGame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

            private void receiveInitialization(Command command) throws IOException{
                switch(Protocol.Input.valueOf(command.getCommand())){
                    case YOU_ARE_CONNECTED:
                        this.userInterface.printConnectionConfirmation();
                        break;
                    case START_THE_GAME:
                        this.userInterface.printGameStartsInfo();
                        break;
                    case YOUR_BOARD_SYMBOL:
                    	this.symbolOnGameBoard = command.getAdditionalValues().get(0);
                    	this.userInterface.printAssignedSymbol(this.symbolOnGameBoard.charAt(0));
                        break;
                    case ANOTHER_PLAYER_COORDINATES:
                        String anotherPlayerCoordinates = command.getAdditionalValues().get(0);
                        if(symbolOnGameBoard.equals("X"))
                            gameLogic.getBoard().setFieldValue(anotherPlayerCoordinates, "O");
                        else
                            gameLogic.getBoard().setFieldValue(anotherPlayerCoordinates, "X");    
                        userInterface.printBoard(gameLogic.getBoard());
                        this.userInterface.printOponentsMoveCoordinates(anotherPlayerCoordinates);
                        break;
                }
            }

            private void validate(Command command){
                if(Protocol.Input.valueOf(command.getCommand()) == Protocol.Input.INCORRECT_MOVE){
                    System.out.println("Incorrect move. Input correct coordinates.");
                    gameLogic.makeNewMove();
                }
            }     

            private void runGameLogic(Command command){
                this.gameLogic.run(command);
            }

        private class GameLogic{
            private final GameBoard board;
            private String newMoveCoordinates = null;
            
            public GameLogic(){
                this.board = new GameBoard();
            }

            private void run(Command command){
                switch(Protocol.Input.valueOf(command.getCommand())){
                    case WAIT_FOR_YOUR_TURN:
                        userInterface.printBoard(this.board);
                        userInterface.printWaitForYourTurnInfo();
                        break;
                    case NEW_MOVE:
                        userInterface.printInputCoordinatesInfo();
                        newMoveCoordinates = makeNewMove();
                        break;
                    case NEXT_PLAYER_TURN:
                        userInterface.printNextPlayerMoveInfo();
                        gameLogic.getBoard().setFieldValue(newMoveCoordinates, symbolOnGameBoard);
                        break;
                    case YOU_WON:
                        gameLogic.getBoard().setFieldValue(newMoveCoordinates, symbolOnGameBoard);
                        userInterface.printBoard(this.board);
                        userInterface.printVictoryInfo();
                        gameIsNotOver = false;
                        break;
                    case YOU_LOST:
                        userInterface.printBoard(this.board);
                        userInterface.printLoseInfo();
                        gameIsNotOver = false;
                        break;
                }
            }

                private String makeNewMove(){
                    try {
                        this.newMoveCoordinates = userIn.nextLine();
                        if(!communication.writeCoordinates(newMoveCoordinates)){
                            userInterface.printIncorrectMoveInfo();
                            return makeNewMove();
                        }        
                        return this.newMoveCoordinates;
                    } catch (IOException ex) {
                        Logger.getLogger(ClientGomokuGame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return null;
                }

            public GameBoard getBoard(){
                return this.board;
            }
        }    

        private class UserInterface{
            public void printOponentsMoveCoordinates(final String anotherPlayerCoordinates){
                System.out.println("Your oponent new move is " + anotherPlayerCoordinates + ":");
            }
            public void printAssignedSymbol(final char symbol){
                System.out.println("Your symbol is: " + symbol);
            }
            public void printConnectionConfirmation(){
                System.out.println("You are now connected to the server.");
            }
            public void printGameStartsInfo(){
                System.out.println("The game starts.");
            }
            public void printLoseInfo(){
                System.out.println("You lost");
            }
            public void printVictoryInfo(){
                System.out.println("You are victorious!");
            }
            public void printNextPlayerMoveInfo(){
                System.out.println("Next player turn. Wait your turn.");
            }
            public void printInputCoordinatesInfo(){
                System.out.println("Your turn. Input coordinates.");
            }
            public void printWaitForYourTurnInfo(){
                System.out.println("Wait your turn.");
            }
            public void printIncorrectMoveInfo(){
                System.out.println("Incorrect coordinates format. Enter coordinates again.");
            }
            
            public void printBoard(final GameBoard gameBoard){
                String[] letters = new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};
                String[] numbers = new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
                String board = "     " + Joiner.on("   ").join(numbers) + "\n";
                for(int i = 0; i < gameBoard.BOARD_SIZE; i++){
                    board = board + "   +---+---+---+---+---+---+---+---+---+---+\n"; 
                    board = board + " " + letters[i] + " |";
                    board = board + " " + Joiner.on(" | ").useForNull(" ").join(Arrays.copyOfRange(gameBoard.getValues(), 10*i, 10*i+10)) + " |\n";
                }
                board = board + "   +---+---+---+---+---+---+---+---+---+---+\n"; 
                System.out.println(board);
            }
        }    

        private class GameBoard{
            public final short BOARD_SIZE = 10;
            private final String[] fields;
            
            public GameBoard(){
                this.fields = new String[BOARD_SIZE*BOARD_SIZE];
            }

            public void setFieldValue(String coordinates, String newValue){
                fields[convertMoveCoordinatesIntoIndex(coordinates)] = newValue;
            }

                private int convertMoveCoordinatesIntoIndex(String coordinates){
                    String[] letters = {"A","B","C","D","E","F","G","H","I","J"};
                    int row = Arrays.asList(letters).indexOf(coordinates.substring(0,1));
                    int cell = Integer.parseInt(coordinates.substring(1,2));
                    return 10*row + cell;
                }

            public String[] getValues(){
                return fields;
            }
        }
    }
}