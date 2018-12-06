
import ClientCrossAndCircleGame.ClientGomokuGame;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author noran
 */
public class TestClient {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        new ClientGomokuGame("localhost",2000).run();
    }
    
}
