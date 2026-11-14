package craftinspector;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static int historySize = 10;
    public static boolean recordCancelledOrders = true;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        historySize = configuration.getInt(
            "historySize",
            Configuration.CATEGORY_GENERAL,
            historySize,
            1,
            100,
            "How many finished crafting orders are kept per ME network.");
        recordCancelledOrders = configuration.getBoolean(
            "recordCancelledOrders",
            Configuration.CATEGORY_GENERAL,
            recordCancelledOrders,
            "Keep cancelled crafting orders in the history too.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
