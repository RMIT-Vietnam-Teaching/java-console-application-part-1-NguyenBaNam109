package claimshield.io;

/**
 * @author Nguyen Ba Nam S3974998
 */

import claimshield.model.Claim;
import claimshield.model.Customer;
import claimshield.model.InsuranceCard;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates between entity objects and the single text line used to store them.
 *
 * The chosen record format is pipe separated, one record per line:
 *
 * <pre>
 * customers.txt  id|fullName|customerType|parentPolicyHolderId
 * cards.txt      cardNumber|cardHolderId|policyOwnerId|expirationDate
 * claims.txt     id|claimDate|insuredPersonId|cardNumber|examDate|documents|amount|status
 * </pre>
 *
 * A pipe is used instead of a comma because names such as "Nguyen Thi Mai, Jr"
 * would otherwise split a record in two. Documents are joined with a semicolon,
 * the literal null marks an absent policy holder, and all three date fields are
 * written as ISO-8601 text so they stay human readable inside the data file.
 */
public final class RecordMapper {

    /** Separates the fields of one record. */
    public static final String FIELD_SEPARATOR = "|";

    /** Separates the document file names inside the documents field. */
    public static final String DOCUMENT_SEPARATOR = ";";

    /** Literal written when a customer has no parent policy holder. */
    public static final String NULL_MARKER = "null";

    /** Marks a comment line inside a data file. */
    public static final String COMMENT_PREFIX = "#";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int CUSTOMER_FIELDS = 4;
    private static final int CARD_FIELDS = 4;
    private static final int CLAIM_FIELDS = 8;

    /** Utility class, never instantiated. */
    private RecordMapper() {
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /**
     * @param customer record to serialise
     * @return the storage line for that customer
     */
    public static String toLine(Customer customer) {
        String parent = customer.getParentPolicyHolderId() == null
                ? NULL_MARKER : customer.getParentPolicyHolderId();
        return String.join(FIELD_SEPARATOR,
                customer.getId(),
                customer.getFullName(),
                customer.getCustomerType(),
                parent);
    }

    /**
     * @param card record to serialise
     * @return the storage line for that insurance card
     */
    public static String toLine(InsuranceCard card) {
        return String.join(FIELD_SEPARATOR,
                card.getCardNumber(),
                card.getCardHolderId(),
                card.getPolicyOwnerId(),
                card.getExpirationDate().format(DATE_FORMAT));
    }

    /**
     * @param claim record to serialise
     * @return the storage line for that claim
     */
    public static String toLine(Claim claim) {
        return String.join(FIELD_SEPARATOR,
                claim.getId(),
                claim.getClaimDate().format(DATE_FORMAT),
                claim.getInsuredPersonId(),
                claim.getCardNumber(),
                claim.getExamDate().format(DATE_FORMAT),
                String.join(DOCUMENT_SEPARATOR, claim.getDocuments()),
                String.valueOf(claim.getClaimAmount()),
                claim.getStatus());
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * @param line raw line read from customers.txt
     * @return the customer described by that line
     * @throws IllegalArgumentException when the line is malformed
     */
    public static Customer parseCustomer(String line) {
        String[] fields = split(line, CUSTOMER_FIELDS, "customer");
        String parent = NULL_MARKER.equalsIgnoreCase(fields[3]) || fields[3].isEmpty() ? null : fields[3];
        return new Customer(fields[0], fields[1], fields[2], parent);
    }

    /**
     * @param line raw line read from cards.txt
     * @return the insurance card described by that line
     * @throws IllegalArgumentException when the line is malformed
     */
    public static InsuranceCard parseInsuranceCard(String line) {
        String[] fields = split(line, CARD_FIELDS, "insurance card");
        return new InsuranceCard(fields[0], fields[1], fields[2], parseDate(fields[3], "expiration date"));
    }

    /**
     * @param line raw line read from claims.txt
     * @return the claim described by that line, without its documents
     * @throws IllegalArgumentException when the line is malformed
     */
    public static Claim parseClaim(String line) {
        String[] fields = split(line, CLAIM_FIELDS, "claim");
        double amount;
        try {
            amount = Double.parseDouble(fields[6]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("claim amount \"" + fields[6] + "\" is not a number");
        }
        return new Claim(
                fields[0],
                parseDate(fields[1], "claim date"),
                fields[2],
                fields[3],
                parseDate(fields[4], "exam date"),
                amount,
                fields[7]);
    }

    /**
     * @param line raw line read from claims.txt
     * @return the document file names listed on that line
     * @throws IllegalArgumentException when the line is malformed
     */
    public static List<String> parseClaimDocuments(String line) {
        String[] fields = split(line, CLAIM_FIELDS, "claim");
        List<String> documents = new ArrayList<>();
        if (fields[5].isEmpty()) {
            return documents;
        }
        for (String document : fields[5].split(DOCUMENT_SEPARATOR)) {
            String trimmed = document.trim();
            if (!trimmed.isEmpty()) {
                documents.add(trimmed);
            }
        }
        return documents;
    }

    /**
     * @param line line to test
     * @return true when the line carries no record and can be skipped
     */
    public static boolean isSkippable(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith(COMMENT_PREFIX);
    }

    /**
     * Splits a record line and checks that it carries the expected number of
     * fields. The limit argument of split keeps trailing empty fields, which
     * matters for a claim with no documents.
     *
     * @param line          raw line
     * @param expectedCount number of fields the record type must have
     * @param recordType    label used in the error message
     * @return the trimmed fields of the record
     */
    private static String[] split(String line, int expectedCount, String recordType) {
        String[] fields = line.split("\\" + FIELD_SEPARATOR, -1);
        if (fields.length != expectedCount) {
            throw new IllegalArgumentException("a " + recordType + " record needs " + expectedCount
                    + " fields but this line has " + fields.length);
        }
        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i].trim();
        }
        return fields;
    }

    /**
     * @param value      ISO-8601 text read from the file
     * @param fieldLabel label used in the error message
     * @return the parsed moment
     */
    private static LocalDateTime parseDate(String value, String fieldLabel) {
        try {
            return LocalDateTime.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldLabel + " \"" + value
                    + "\" is not ISO-8601, expected 2026-07-10T14:30:00");
        }
    }
}
