package bguspl.set.ex;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;
    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;
    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    private Dealer dealer;
    private BlockingQueue<Integer> keyPressesTokens;
    private int[] potentialSet;

    private int frozenState;

    private int potentialSetSize;

    private static Object playerLock = new Object();

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.keyPressesTokens = new LinkedBlockingQueue<Integer>();
        this.potentialSet = new int[3];
        for (int i = 0; i < 3; i++) {
            potentialSet[i] = -1;
        }
        this.frozenState = 0;
        this.potentialSetSize = 0;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) {
            createArtificialIntelligence();
        }
        while (!terminate) {
            //TODO: check tokens list, add tokens to the table and check if we reached 3 tokens notify dealer and send him/her tokens
            //check if player is frozen:
            if (keyPressesTokens.size() > 0) {
                int token = keyPressesTokens.remove();
                if (table.getSlotToCard()[token] != null) {
                    int card = table.slotToCard[token];
                    if (potentialSetContains(card)) {
                        removeFromPotentialSet(card);
                        synchronized (table) {
                            table.removeToken(id, token);
                        }
                    } else if (potentialSetSize < 3) {
                        synchronized (table) {
                            table.placeToken(id, token);
                            addToPotentialSet(card);
                        }
                        if (potentialSetSize == 3) {
                            checkPlayer();
                        }
                    }

                    if (frozenState == 1) {
                        point();
                        frozenState = 0;
                    }

                    if (frozenState == 3) {
                        penalty();
                        frozenState = 0;
                    }
                }
            }
        }

        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                Random random = new Random();
                int slot = random.nextInt(env.config.tableSize);

                keyPressed(slot);
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        try {
            playerThread.interrupt();
            terminate = true;
        } catch (SecurityException e) {
        }
        if (!human) {
            try {
                aiThread.interrupt();
                terminate = true;
            } catch (SecurityException e) {
            }
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (frozenState == 0) {
            if (table.slotToCard[slot] != null && keyPressesTokens.size() < 3) {
                keyPressesTokens.add(slot);
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        env.ui.setScore(id, ++score);
        long timer = System.currentTimeMillis() + env.config.pointFreezeMillis + 1000;
        while (System.currentTimeMillis() < timer - 1000) {
            env.ui.setFreeze(id, timer - System.currentTimeMillis());
        }
        env.ui.setFreeze(id, -1000);
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests

    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long timer = System.currentTimeMillis() + env.config.penaltyFreezeMillis + 1000;
        while (System.currentTimeMillis() < timer - 1000) {
            env.ui.setFreeze(id, timer - System.currentTimeMillis());
        }
        env.ui.setFreeze(id, -1000);
    }

    public int score() {
        return score;
    }

    public Queue<Integer> getKeyPressesTokens() {
        return keyPressesTokens;
    }

    public int getId() {
        return id;
    }

    public synchronized int[] getPotentialSet() {
        return potentialSet;
    }

    public void setFrozenState(int i) {
        this.frozenState = i;
    }


    public synchronized int getPotentialSetSize() {
        return potentialSetSize;
    }

    public synchronized void addToPotentialSet(int card) {
        getPotentialSet()[potentialSetSize] = card;
        potentialSetSize++;
    }

    public synchronized void removeFromPotentialSet(int card) {
        boolean found = false;
        for (int i = 0; i < 3 & !found; i++) {
            if (getPotentialSet()[i] == card) {
                found = true;
                for (int j = i; j < 2; j++) {
                    getPotentialSet()[j] = getPotentialSet()[j + 1];
                }
                getPotentialSet()[2] = -1;
                potentialSetSize--;
            }
        }
    }

    public synchronized boolean potentialSetContains(int card) {
        for (int i = 0; i < 3; i++) {
            if (getPotentialSet()[i] == card) {
                return true;
            }
        }
        return false;
    }

    public synchronized void clearSet() {
        for (int i = 0; i < 3; i++) {
            potentialSet[i] = -1;
        }
        potentialSetSize = 0;
    }

    public void checkPlayer() {
        synchronized (this) {
            dealer.enqueuePlayer(this);
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }
    }

    public void notifyPlayer() {
        synchronized (this) {
            this.notifyAll();
        }
    }
    public boolean isValid(int[] potentialSet) {
        return potentialSet[0] != -1 &&
                potentialSet[1] != -1 &&
                potentialSet[2] != -1 &&
                table.getCardToSlot()[potentialSet[0]] != null &&
                table.getCardToSlot()[potentialSet[1]] != null &&
                table.getCardToSlot()[potentialSet[2]] != null;
    }
    public boolean isHuman()
    {
        return human;
    }
}
