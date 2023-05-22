package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealerTest {
    Dealer dealer;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;

    private Table table;

    private Integer[] slotToCard;
    private Integer[] cardToSlot;
    private Player[] players;
    @Mock
    private Logger logger;


    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        TableTest.MockLogger logger = new TableTest.MockLogger();
        Config config = new Config(logger, properties);
        slotToCard = new Integer[config.tableSize];
        cardToSlot = new Integer[config.deckSize];

        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        table = new Table(env, slotToCard, cardToSlot);
        dealer = new Dealer(env, table, players);
    }

    private void fillAllSlots() {
        for (int i = 0; i < slotToCard.length; ++i) {
            slotToCard[i] = i;
            cardToSlot[i] = i;
        }
    }

    @Test
    void checkEnqueuePlayer() {
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        Player player = new Player(env,dealer,table,0,true);
        dealer.enqueuePlayer(player);
        assertTrue(dealer.getPlayersQueue().contains(player));
    }

    @Test
    //tries to insert the same player twice
    void checkEnqueuePlayer2() {
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        Player player = new Player(env,dealer,table,0,true);
        dealer.enqueuePlayer(player);
        dealer.enqueuePlayer(player);
        dealer.getPlayersQueue().remove(player);
        assertFalse(dealer.getPlayersQueue().contains(player));
    }
}