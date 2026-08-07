package claimshield;

/**
 * @author Nguyen Ba Nam S3974998
 */

import claimshield.io.FileStorage;
import claimshield.manager.ClaimManager;
import claimshield.ui.ConsoleMenu;
import claimshield.ui.InputReader;

import java.util.List;
import java.util.Scanner;

/**
 * Entry point of ClaimShield.
 *
 * The class does three things and nothing else: it builds the three layers of
 * the program, it loads the data files into memory, and it starts the console
 * menu. Wiring the objects here rather than inside them keeps the layers
 * independent and easy to test.
 */
public final class ClaimShieldApp {

    /** Folder holding the three data files, relative to the working directory. */
    private static final String DEFAULT_DATA_DIRECTORY = "data";

    /** Utility class, never instantiated. */
    private ClaimShieldApp() {
    }

    /**
     * Starts the application.
     *
     * @param args optional path to the data folder; the default is data
     */
    public static void main(String[] args) {
        String dataDirectory = args.length > 0 ? args[0] : DEFAULT_DATA_DIRECTORY;

        ClaimManager manager = new ClaimManager();
        FileStorage storage = new FileStorage(dataDirectory);

        System.out.println("Loading ClaimShield data from " + dataDirectory + " ...");
        int loaded = storage.loadAll(manager);
        System.out.println("Loaded " + loaded + " record(s): "
                + manager.getAllCustomers().size() + " customers, "
                + manager.getAllInsuranceCards().size() + " insurance cards, "
                + manager.getAllClaims().size() + " claims.");

        List<String> warnings = storage.getWarnings();
        if (!warnings.isEmpty()) {
            System.out.println("Warnings raised while loading:");
            for (String warning : warnings) {
                System.out.println("  [!] " + warning);
            }
        }

        try (Scanner scanner = new Scanner(System.in)) {
            new ConsoleMenu(manager, storage, new InputReader(scanner)).run();
        }
    }
}
