package claimshield.ui;

/**
 * @author Nguyen Ba Nam S3974998
 */

import claimshield.io.FileStorage;
import claimshield.manager.ClaimManager;
import claimshield.model.Claim;
import claimshield.model.Customer;
import claimshield.model.InsuranceCard;
import claimshield.util.Validator;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The text based front end of ClaimShield.
 *
 * This class owns presentation only. It gathers input, calls a single manager
 * operation and reports what came back, so a rule is never duplicated between
 * the screens and the data layer. Every screen offers a way back to the previous
 * one, and every prompt accepts the word cancel, so the administrator is never
 * trapped part way through an operation.
 */
public class ConsoleMenu {

    private static final String DIVIDER = "-".repeat(78);

    private final ClaimManager manager;
    private final FileStorage storage;
    private final InputReader input;
    private boolean running;

    /**
     * @param manager data layer holding every record
     * @param storage persistence layer used by the save and exit option
     * @param input   console input helper
     */
    public ConsoleMenu(ClaimManager manager, FileStorage storage, InputReader input) {
        this.manager = manager;
        this.storage = storage;
        this.input = input;
        this.running = true;
    }

    /** Runs the main menu loop until the administrator exits. */
    public void run() {
        while (running) {
            printMainMenu();
            int choice = input.readMenuChoice("Select an option (1-4): ", 1, 4);
            if (choice == InputReader.END_OF_INPUT) {
                System.out.println();
                System.out.println("Input stream closed. Saving before shutdown.");
                saveAndExit();
                return;
            }
            switch (choice) {
                case 1 -> customerMenu();
                case 2 -> insuranceCardMenu();
                case 3 -> claimMenu();
                case 4 -> saveAndExit();
                default -> printError("Unknown option.");
            }
        }
    }

    // ------------------------------------------------------------------
    // Menus
    // ------------------------------------------------------------------

    /** Prints the top level menu together with a snapshot of the data set. */
    private void printMainMenu() {
        System.out.println();
        System.out.println(DIVIDER);
        System.out.println("  CLAIMSHIELD - Health Insurance Administration Console");
        System.out.printf("  %d customers | %d insurance cards | %d claims in memory%n",
                manager.getAllCustomers().size(),
                manager.getAllInsuranceCards().size(),
                manager.getAllClaims().size());
        System.out.println(DIVIDER);
        System.out.println("  1. Manage Customer Directory");
        System.out.println("  2. Manage Insurance Cards");
        System.out.println("  3. Process Claims");
        System.out.println("  4. Save and Exit");
        System.out.println(DIVIDER);
    }

    /** Customer sub menu loop. */
    private void customerMenu() {
        boolean inMenu = true;
        while (inMenu) {
            printSubMenu("MANAGE CUSTOMER DIRECTORY", new String[]{
                    "Add a customer",
                    "View all customers",
                    "Find a customer by id",
                    "Update a customer",
                    "Remove a customer"});
            int choice = input.readMenuChoice("Select an option (0-5): ", 0, 5);
            switch (choice) {
                case InputReader.END_OF_INPUT, 0 -> inMenu = false;
                case 1 -> addCustomer();
                case 2 -> viewAllCustomers();
                case 3 -> findCustomer();
                case 4 -> updateCustomer();
                case 5 -> removeCustomer();
                default -> printError("Unknown option.");
            }
        }
    }

    /** Insurance card sub menu loop. */
    private void insuranceCardMenu() {
        boolean inMenu = true;
        while (inMenu) {
            printSubMenu("MANAGE INSURANCE CARDS", new String[]{
                    "Register a new card",
                    "View all cards",
                    "Find a card by number",
                    "Update a card expiry date",
                    "Remove a card"});
            int choice = input.readMenuChoice("Select an option (0-5): ", 0, 5);
            switch (choice) {
                case InputReader.END_OF_INPUT, 0 -> inMenu = false;
                case 1 -> registerCard();
                case 2 -> viewAllCards();
                case 3 -> findCard();
                case 4 -> updateCardExpiry();
                case 5 -> removeCard();
                default -> printError("Unknown option.");
            }
        }
    }

    /** Claim sub menu loop. */
    private void claimMenu() {
        boolean inMenu = true;
        while (inMenu) {
            printSubMenu("PROCESS CLAIMS", new String[]{
                    "Create a new claim",
                    "Attach a document to a claim",
                    "Update a claim status",
                    "Update a claim amount",
                    "View all claims",
                    "Find a claim by id",
                    "Remove a claim"});
            int choice = input.readMenuChoice("Select an option (0-7): ", 0, 7);
            switch (choice) {
                case InputReader.END_OF_INPUT, 0 -> inMenu = false;
                case 1 -> createClaim();
                case 2 -> attachDocument();
                case 3 -> updateClaimStatus();
                case 4 -> updateClaimAmount();
                case 5 -> viewAllClaims();
                case 6 -> findClaim();
                case 7 -> removeClaim();
                default -> printError("Unknown option.");
            }
        }
    }

    // ------------------------------------------------------------------
    // Customer operations
    // ------------------------------------------------------------------

    /** Collects a new customer profile and hands it to the manager. */
    private void addCustomer() {
        printHeading("ADD A CUSTOMER");
        System.out.println("  1. PolicyHolder (pays for their own plan)");
        System.out.println("  2. Dependent (covered by a policy holder)");
        System.out.println("  0. Back");
        int typeChoice = input.readMenuChoice("Customer type (0-2): ", 0, 2);
        if (typeChoice <= 0) {
            return;
        }
        String customerType = typeChoice == 1 ? Customer.TYPE_POLICY_HOLDER : Customer.TYPE_DEPENDENT;

        String fullName = input.readText("Full name: ");
        if (fullName == null) {
            printCancelled();
            return;
        }

        String parentId = null;
        if (Customer.TYPE_DEPENDENT.equals(customerType)) {
            listPolicyHolders();
            parentId = input.readPattern("Policy holder id: ",
                    Validator.CUSTOMER_ID_REGEX, "c- followed by 7 digits, for example c-1234567");
            if (parentId == null) {
                printCancelled();
                return;
            }
        }

        String id = askForId("customer", manager.generateCustomerId(),
                Validator.CUSTOMER_ID_REGEX, "c- followed by 7 digits, for example c-1234567");
        if (id == null) {
            printCancelled();
            return;
        }

        try {
            manager.addCustomer(new Customer(id, fullName, customerType, parentId));
            printSuccess("Customer " + id + " added.");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
        }
    }

    /** Prints the whole customer directory. */
    private void viewAllCustomers() {
        printHeading("CUSTOMER DIRECTORY");
        List<Customer> all = manager.getAllCustomers();
        if (all.isEmpty()) {
            System.out.println("  No customers are registered yet.");
            return;
        }
        for (Customer customer : all) {
            System.out.println("  " + customer);
        }
        System.out.println("  " + all.size() + " customer(s) listed.");
    }

    /** Prints one customer together with their card and claims. */
    private void findCustomer() {
        printHeading("FIND A CUSTOMER");
        String id = input.readText("Customer id: ");
        if (id == null) {
            printCancelled();
            return;
        }
        Customer customer = manager.getCustomerById(id);
        if (customer == null) {
            printError("No customer found with id " + id + ".");
            return;
        }
        System.out.println("  " + customer);
        InsuranceCard card = manager.getCardByHolderId(id);
        System.out.println("  Card: " + (card == null ? "none registered" : card.getCardNumber()));
        List<Claim> claims = manager.getClaimsByCustomer(id);
        if (claims.isEmpty()) {
            System.out.println("  Claims: none");
            return;
        }
        System.out.println("  Claims:");
        for (Claim claim : claims) {
            System.out.println("    " + claim);
        }
    }

    /** Edits the mutable fields of an existing customer. */
    private void updateCustomer() {
        printHeading("UPDATE A CUSTOMER");
        String id = input.readText("Customer id: ");
        if (id == null) {
            printCancelled();
            return;
        }
        Customer customer = manager.getCustomerById(id);
        if (customer == null) {
            printError("No customer found with id " + id + ".");
            return;
        }
        System.out.println("  Current record: " + customer);

        String fullName = input.readText("New full name: ");
        if (fullName == null) {
            printCancelled();
            return;
        }
        System.out.println("  1. PolicyHolder");
        System.out.println("  2. Dependent");
        System.out.println("  0. Back");
        int typeChoice = input.readMenuChoice("New customer type (0-2): ", 0, 2);
        if (typeChoice <= 0) {
            printCancelled();
            return;
        }
        String customerType = typeChoice == 1 ? Customer.TYPE_POLICY_HOLDER : Customer.TYPE_DEPENDENT;
        String parentId = null;
        if (Customer.TYPE_DEPENDENT.equals(customerType)) {
            listPolicyHolders();
            parentId = input.readPattern("Policy holder id: ",
                    Validator.CUSTOMER_ID_REGEX, "c- followed by 7 digits");
            if (parentId == null) {
                printCancelled();
                return;
            }
        }

        try {
            manager.updateCustomer(id, fullName, customerType, parentId);
            printSuccess("Customer " + id + " updated.");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
        }
    }

    /** Deletes a customer once nothing points at them any more. */
    private void removeCustomer() {
        printHeading("REMOVE A CUSTOMER");
        String id = input.readText("Customer id: ");
        if (id == null) {
            printCancelled();
            return;
        }
        Customer customer = manager.getCustomerById(id);
        if (customer == null) {
            printError("No customer found with id " + id + ".");
            return;
        }
        System.out.println("  " + customer);
        if (!input.readYesNo("Remove this customer permanently? (y/n): ")) {
            printCancelled();
            return;
        }
        try {
            manager.deleteCustomer(id);
            printSuccess("Customer " + id + " removed.");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Insurance card operations
    // ------------------------------------------------------------------

    /** Registers a card and links it to a customer. */
    private void registerCard() {
        printHeading("REGISTER AN INSURANCE CARD");
        String holderId = input.readPattern("Card holder id: ",
                Validator.CUSTOMER_ID_REGEX, "c- followed by 7 digits, for example c-1234567");
        if (holderId == null) {
            printCancelled();
            return;
        }
        Customer holder = manager.getCustomerById(holderId);
        if (holder == null) {
            printError("No customer found with id " + holderId + ".");
            return;
        }
        String policyOwnerId = holder.isPolicyHolder() ? holder.getId() : holder.getParentPolicyHolderId();
        System.out.println("  Holder: " + holder.getFullName() + " (" + holder.getCustomerType() + ")");
        System.out.println("  Plan is paid by policy holder " + policyOwnerId + ".");

        LocalDateTime expiry = input.readDateTime("Expiration date (yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss): ", false);
        if (expiry == null) {
            printCancelled();
            return;
        }

        String cardNumber = askForId("card number", manager.generateCardNumber(),
                Validator.CARD_NUMBER_REGEX, "exactly 10 digits, for example 1000000001");
        if (cardNumber == null) {
            printCancelled();
            return;
        }

        try {
            manager.addInsuranceCard(new InsuranceCard(cardNumber, holderId, policyOwnerId, expiry));
            printSuccess("Card " + cardNumber + " registered to " + holderId + ".");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
        }
    }

    /** Prints every registered card. */
    private void viewAllCards() {
        printHeading("INSURANCE CARDS");
        List<InsuranceCard> all = manager.getAllInsuranceCards();
        if (all.isEmpty()) {
            System.out.println("  No insurance cards are registered yet.");
            return;
        }
        for (InsuranceCard card : all) {
            System.out.println("  " + card);
        }
        System.out.println("  " + all.size() + " card(s) listed.");
    }

    /** Prints a single card and the customer holding it. */
    private void findCard() {
        printHeading("FIND AN INSURANCE CARD");
        String cardNumber = input.readText("Card number: ");
        if (cardNumber == null) {
            printCancelled();
            return;
        }
        InsuranceCard card = manager.getCardByNumber(cardNumber);
        if (card == null) {
            printError("No insurance card found with number " + cardNumber + ".");
            return;
        }
        System.out.println("  " + card);
        Customer holder = manager.getCustomerById(card.getCardHolderId());
        if (holder != null) {
            System.out.println("  Held by: " + holder.getFullName());
        }
        System.out.println("  Still valid: " + (card.isValidAt(LocalDateTime.now()) ? "yes" : "no, expired"));
    }

    /** Moves the expiry date of a card. */
    private void updateCardExpiry() {
        printHeading("UPDATE A CARD EXPIRY DATE");
        String cardNumber = input.readText("Card number: ");
        if (cardNumber == null) {
            printCancelled();
            return;
        }
        InsuranceCard card = manager.getCardByNumber(cardNumber);
        if (card == null) {
            printError("No insurance card found with number " + cardNumber + ".");
            return;
        }
        System.out.println("  Current expiry: " + card.getExpirationDate());
        LocalDateTime expiry = input.readDateTime("New expiration date: ", false);
        if (expiry == null) {
            printCancelled();
            return;
        }
        try {
            manager.updateCardExpiration(cardNumber, expiry);
            printSuccess("Card " + cardNumber + " now expires on " + expiry + ".");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
        }
    }

    /** Deletes a card that no claim refers to. */
    private void removeCard() {
        printHeading("REMOVE AN INSURANCE CARD");
        String cardNumber = input.readText("Card number: ");
        if (cardNumber == null) {
            printCancelled();
            return;
        }
        InsuranceCard card = manager.getCardByNumber(cardNumber);
        if (card == null) {
            printError("No insurance card found with number " + cardNumber + ".");
            return;
        }
        System.out.println("  " + card);
        if (!input.readYesNo("Remove this card permanently? (y/n): ")) {
            printCancelled();
            return;
        }
        try {
            manager.deleteInsuranceCard(cardNumber);
            printSuccess("Card " + cardNumber + " removed.");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Claim operations
    // ------------------------------------------------------------------

    /** Files a new claim for a customer and optionally attaches documents. */
    private void createClaim() {
        printHeading("CREATE A NEW CLAIM");
        String insuredId = input.readPattern("Insured person id: ",
                Validator.CUSTOMER_ID_REGEX, "c- followed by 7 digits, for example c-1234567");
        if (insuredId == null) {
            printCancelled();
            return;
        }
        Customer insured = manager.getCustomerById(insuredId);
        if (insured == null) {
            printError("No customer found with id " + insuredId + ".");
            return;
        }
        InsuranceCard card = manager.getCardByHolderId(insuredId);
        if (card == null) {
            printError("Customer " + insuredId + " holds no insurance card. Register one first.");
            return;
        }
        System.out.println("  Insured: " + insured.getFullName());
        System.out.println("  Card " + card.getCardNumber() + ", expires " + card.getExpirationDate());

        LocalDateTime examDate = input.readDateTime("Examination date: ", false);
        if (examDate == null) {
            printCancelled();
            return;
        }
        LocalDateTime claimDate = input.readDateTime("Claim date (Enter for now): ", true);
        if (claimDate == null) {
            printCancelled();
            return;
        }
        Double amount = input.readPositiveAmount("Claim amount: ");
        if (amount == null) {
            printCancelled();
            return;
        }
        String claimId = askForId("claim", manager.generateClaimId(),
                Validator.CLAIM_ID_REGEX, "f- followed by 10 digits, for example f-1234567890");
        if (claimId == null) {
            printCancelled();
            return;
        }

        try {
            manager.addClaim(new Claim(claimId, claimDate, insuredId, card.getCardNumber(),
                    examDate, amount, Claim.STATUS_NEW));
            printSuccess("Claim " + claimId + " created with status New.");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
            return;
        }

        while (input.readYesNo("Attach a document now? (y/n): ")) {
            attachDocumentTo(claimId);
        }
    }

    /** Asks which claim to attach a document to, then attaches it. */
    private void attachDocument() {
        printHeading("ATTACH A DOCUMENT");
        String claimId = input.readText("Claim id: ");
        if (claimId == null) {
            printCancelled();
            return;
        }
        if (manager.getClaimById(claimId) == null) {
            printError("No claim found with id " + claimId + ".");
            return;
        }
        attachDocumentTo(claimId);
    }

    /**
     * Attaches one document file name to a known claim.
     *
     * @param claimId id of an existing claim
     */
    private void attachDocumentTo(String claimId) {
        Claim claim = manager.getClaimById(claimId);
        String requiredPrefix = claim.getId() + "_" + claim.getCardNumber() + "_";
        System.out.println("  File name must look like " + requiredPrefix + "DocumentName.pdf");
        String shortName = input.readText("Document name (without the prefix and .pdf): ");
        if (shortName == null) {
            printCancelled();
            return;
        }
        String fileName = shortName.startsWith(requiredPrefix) ? shortName : requiredPrefix + shortName;
        if (!fileName.endsWith(".pdf")) {
            fileName = fileName + ".pdf";
        }
        try {
            manager.addDocumentToClaim(claimId, fileName);
            printSuccess("Attached " + fileName + ".");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
        }
    }

    /** Moves a claim forward through the workflow. */
    private void updateClaimStatus() {
        printHeading("UPDATE A CLAIM STATUS");
        String claimId = input.readText("Claim id: ");
        if (claimId == null) {
            printCancelled();
            return;
        }
        Claim claim = manager.getClaimById(claimId);
        if (claim == null) {
            printError("No claim found with id " + claimId + ".");
            return;
        }
        System.out.println("  " + claim.toDetailedString());
        System.out.println("  Current status: " + claim.getStatus());
        System.out.println("  1. New");
        System.out.println("  2. Processing");
        System.out.println("  3. Done");
        System.out.println("  0. Back");
        int choice = input.readMenuChoice("New status (0-3): ", 0, 3);
        if (choice <= 0) {
            printCancelled();
            return;
        }
        String newStatus = Claim.STATUS_FLOW.get(choice - 1);
        try {
            manager.updateClaimStatus(claimId, newStatus);
            printSuccess("Claim " + claimId + " is now " + newStatus + ".");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
        }
    }

    /** Reprices a claim that has not been settled yet. */
    private void updateClaimAmount() {
        printHeading("UPDATE A CLAIM AMOUNT");
        String claimId = input.readText("Claim id: ");
        if (claimId == null) {
            printCancelled();
            return;
        }
        Claim claim = manager.getClaimById(claimId);
        if (claim == null) {
            printError("No claim found with id " + claimId + ".");
            return;
        }
        System.out.printf("  Current amount: %,.2f%n", claim.getClaimAmount());
        Double amount = input.readPositiveAmount("New claim amount: ");
        if (amount == null) {
            printCancelled();
            return;
        }
        try {
            manager.updateClaimAmount(claimId, amount);
            printSuccess("Claim " + claimId + " repriced.");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
        }
    }

    /** Prints every claim in the system. */
    private void viewAllClaims() {
        printHeading("CLAIMS");
        List<Claim> all = manager.getAllClaims();
        if (all.isEmpty()) {
            System.out.println("  No claims have been filed yet.");
            return;
        }
        double total = 0;
        for (Claim claim : all) {
            System.out.println("  " + claim);
            total += claim.getClaimAmount();
        }
        System.out.printf("  %d claim(s) listed, %,.2f claimed in total.%n", all.size(), total);
    }

    /** Prints one claim with its full document list. */
    private void findClaim() {
        printHeading("FIND A CLAIM");
        String claimId = input.readText("Claim id: ");
        if (claimId == null) {
            printCancelled();
            return;
        }
        Claim claim = manager.getClaimById(claimId);
        if (claim == null) {
            printError("No claim found with id " + claimId + ".");
            return;
        }
        System.out.println("  " + claim.toDetailedString());
    }

    /** Deletes a claim after confirmation. */
    private void removeClaim() {
        printHeading("REMOVE A CLAIM");
        String claimId = input.readText("Claim id: ");
        if (claimId == null) {
            printCancelled();
            return;
        }
        Claim claim = manager.getClaimById(claimId);
        if (claim == null) {
            printError("No claim found with id " + claimId + ".");
            return;
        }
        System.out.println("  " + claim);
        if (!input.readYesNo("Remove this claim permanently? (y/n): ")) {
            printCancelled();
            return;
        }
        try {
            manager.deleteClaim(claimId);
            printSuccess("Claim " + claimId + " removed.");
        } catch (IllegalArgumentException exception) {
            printError(exception.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Exit
    // ------------------------------------------------------------------

    /** Writes every record back to disk and stops the main loop. */
    private void saveAndExit() {
        printHeading("SAVE AND EXIT");
        try {
            int written = storage.saveAll(manager);
            printSuccess(written + " record(s) written back to the data files.");
        } catch (IOException exception) {
            printError("Could not write the data files: " + exception.getMessage());
            if (!input.readYesNo("Exit anyway and lose the changes? (y/n): ")) {
                return;
            }
        }
        System.out.println("Goodbye.");
        running = false;
    }

    // ------------------------------------------------------------------
    // Shared presentation helpers
    // ------------------------------------------------------------------

    /**
     * Prints a sub menu with an automatic Back entry.
     *
     * @param title   heading of the sub menu
     * @param options entries shown from number 1 upwards
     */
    private void printSubMenu(String title, String[] options) {
        System.out.println();
        System.out.println(DIVIDER);
        System.out.println("  " + title);
        System.out.println(DIVIDER);
        for (int i = 0; i < options.length; i++) {
            System.out.println("  " + (i + 1) + ". " + options[i]);
        }
        System.out.println("  0. Back to main menu");
        System.out.println(DIVIDER);
    }

    /**
     * Offers an auto generated id and lets the administrator type their own.
     *
     * @param label       word used in the prompts, for example customer
     * @param suggestedId next free id proposed by the manager
     * @param regex       pattern a typed id must match
     * @param formatHint  description of the pattern
     * @return the chosen id, or null when the operation was cancelled
     */
    private String askForId(String label, String suggestedId, String regex, String formatHint) {
        if (input.readYesNo("Use the next free " + label + " " + suggestedId + "? (y/n): ")) {
            return suggestedId;
        }
        return input.readPattern("Enter the " + label + ": ", regex, formatHint);
    }

    /** Prints the policy holders an administrator can attach a dependent to. */
    private void listPolicyHolders() {
        System.out.println("  Available policy holders:");
        boolean found = false;
        for (Customer customer : manager.getAllCustomers()) {
            if (customer.isPolicyHolder()) {
                System.out.println("    " + customer.getId() + "  " + customer.getFullName());
                found = true;
            }
        }
        if (!found) {
            System.out.println("    (none yet, add a PolicyHolder first)");
        }
    }

    /**
     * @param title heading of an operation screen
     */
    private void printHeading(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    /**
     * @param message confirmation to show the administrator
     */
    private void printSuccess(String message) {
        System.out.println("  [OK] " + message);
    }

    /**
     * @param message explanation of why an operation was refused
     */
    private void printError(String message) {
        System.out.println("  [!] " + message);
    }

    /** Tells the administrator that nothing was changed. */
    private void printCancelled() {
        System.out.println("  Operation cancelled, nothing was changed.");
    }
}
