package claimshield.io;

/**
 * @author Nguyen Ba Nam S3974998
 */

import claimshield.manager.ClaimManager;
import claimshield.model.Claim;
import claimshield.model.Customer;
import claimshield.model.InsuranceCard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the three data files into a {@link ClaimManager} at startup and writes
 * the whole in-memory state back when the administrator exits.
 *
 * Loading is deliberately fault tolerant. A single malformed or rule-breaking
 * line is reported as a warning and skipped rather than allowed to crash the
 * program, so an administrator can always start the system, see exactly which
 * line is wrong and repair it. Files are read in dependency order, because a
 * card needs its customer and a claim needs both its customer and its card.
 *
 * Saving is protected the other way round: each file is first written to a
 * temporary neighbour, and the three real files are swapped in only after all
 * of them were written successfully, so an interrupted save cannot leave a
 * half written data file behind.
 */
public class FileStorage {

    private static final String CUSTOMER_FILE = "customers.txt";
    private static final String CARD_FILE = "insurance_cards.txt";
    private static final String CLAIM_FILE = "claims.txt";

    private static final String CUSTOMER_HEADER =
            "# id|fullName|customerType|parentPolicyHolderId";
    private static final String CARD_HEADER =
            "# cardNumber|cardHolderId|policyOwnerId|expirationDate";
    private static final String CLAIM_HEADER =
            "# id|claimDate|insuredPersonId|cardNumber|examDate|documents|claimAmount|status";

    private final Path dataDirectory;
    private final List<String> warnings;

    /**
     * @param dataDirectory folder holding the three data files, for example data
     */
    public FileStorage(String dataDirectory) {
        this.dataDirectory = Paths.get(dataDirectory);
        this.warnings = new ArrayList<>();
    }

    /**
     * @return the messages produced by the most recent load or save
     */
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    /**
     * Populates the manager from disk.
     *
     * @param manager empty manager to fill
     * @return the number of records successfully loaded
     */
    public int loadAll(ClaimManager manager) {
        warnings.clear();
        int loaded = 0;
        loaded += loadCustomers(manager);
        loaded += loadInsuranceCards(manager);
        loaded += loadClaims(manager);
        return loaded;
    }

    /**
     * Writes every record currently held in memory back to disk.
     *
     * @param manager manager holding the live records
     * @return the number of records written
     * @throws IOException when a data file cannot be written
     */
    public int saveAll(ClaimManager manager) throws IOException {
        warnings.clear();
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }

        List<String> customerLines = new ArrayList<>();
        customerLines.add(CUSTOMER_HEADER);
        for (Customer customer : manager.getAllCustomers()) {
            customerLines.add(RecordMapper.toLine(customer));
        }

        List<String> cardLines = new ArrayList<>();
        cardLines.add(CARD_HEADER);
        for (InsuranceCard card : manager.getAllInsuranceCards()) {
            cardLines.add(RecordMapper.toLine(card));
        }

        List<String> claimLines = new ArrayList<>();
        claimLines.add(CLAIM_HEADER);
        for (Claim claim : manager.getAllClaims()) {
            claimLines.add(RecordMapper.toLine(claim));
        }

        Path customerTmp = writeTemp(CUSTOMER_FILE, customerLines);
        Path cardTmp = writeTemp(CARD_FILE, cardLines);
        Path claimTmp = writeTemp(CLAIM_FILE, claimLines);
        Files.move(customerTmp, dataDirectory.resolve(CUSTOMER_FILE), StandardCopyOption.REPLACE_EXISTING);
        Files.move(cardTmp, dataDirectory.resolve(CARD_FILE), StandardCopyOption.REPLACE_EXISTING);
        Files.move(claimTmp, dataDirectory.resolve(CLAIM_FILE), StandardCopyOption.REPLACE_EXISTING);

        return (customerLines.size() - 1) + (cardLines.size() - 1) + (claimLines.size() - 1);
    }

    /**
     * Writes the lines of one data file to a temporary file next to the real
     * one. saveAll writes all three temporary files first and only then swaps
     * them into place.
     *
     * @param fileName real name of the data file
     * @param lines    content to write
     * @return the path of the temporary file
     * @throws IOException when the temporary file cannot be written
     */
    private Path writeTemp(String fileName, List<String> lines) throws IOException {
        Path tmp = dataDirectory.resolve(fileName + ".tmp");
        Files.write(tmp, lines, StandardCharsets.UTF_8);
        return tmp;
    }

    /**
     * @param manager manager to fill
     * @return the number of customers loaded
     */
    private int loadCustomers(ClaimManager manager) {
        List<String> lines = readLines(CUSTOMER_FILE);
        int loaded = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (RecordMapper.isSkippable(line)) {
                continue;
            }
            try {
                manager.addCustomer(RecordMapper.parseCustomer(line));
                loaded++;
            } catch (IllegalArgumentException exception) {
                warnings.add(skipMessage(CUSTOMER_FILE, i + 1, exception.getMessage()));
            }
        }
        return loaded;
    }

    /**
     * @param manager manager to fill
     * @return the number of insurance cards loaded
     */
    private int loadInsuranceCards(ClaimManager manager) {
        List<String> lines = readLines(CARD_FILE);
        int loaded = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (RecordMapper.isSkippable(line)) {
                continue;
            }
            try {
                manager.addInsuranceCard(RecordMapper.parseInsuranceCard(line));
                loaded++;
            } catch (IllegalArgumentException exception) {
                warnings.add(skipMessage(CARD_FILE, i + 1, exception.getMessage()));
            }
        }
        return loaded;
    }

    /**
     * @param manager manager to fill
     * @return the number of claims loaded
     */
    private int loadClaims(ClaimManager manager) {
        List<String> lines = readLines(CLAIM_FILE);
        int loaded = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (RecordMapper.isSkippable(line)) {
                continue;
            }
            try {
                manager.restoreClaim(RecordMapper.parseClaim(line), RecordMapper.parseClaimDocuments(line));
                loaded++;
            } catch (IllegalArgumentException exception) {
                warnings.add(skipMessage(CLAIM_FILE, i + 1, exception.getMessage()));
            }
        }
        return loaded;
    }

    /**
     * Reads a data file, recording a warning instead of failing when the file is
     * missing or unreadable.
     *
     * @param fileName name of the file inside the data folder
     * @return the lines of the file, or an empty list
     */
    private List<String> readLines(String fileName) {
        Path file = dataDirectory.resolve(fileName);
        if (!Files.exists(file)) {
            warnings.add("Data file " + file + " was not found, starting with no records from it.");
            return new ArrayList<>();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            warnings.add("Data file " + file + " could not be read: " + exception.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * @param fileName   file the line came from
     * @param lineNumber 1-based position of the line
     * @param reason     explanation produced by the parser or the manager
     * @return a warning message an administrator can act on
     */
    private String skipMessage(String fileName, int lineNumber, String reason) {
        return "Skipped " + fileName + " line " + lineNumber + ": " + reason;
    }
}
