package claimshield.model;

/**
 * @author Nguyen Ba Nam S3974998
 */

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a formal request for payment raised against a medical event.
 * The document list is kept private and only exposed as an unmodifiable view so
 * that callers cannot bypass the format checks applied by the manager layer.
 */
public class Claim {

    /** Status of a claim that has just been filed. */
    public static final String STATUS_NEW = "New";

    /** Status of a claim currently being assessed. */
    public static final String STATUS_PROCESSING = "Processing";

    /** Status of a fully settled claim. */
    public static final String STATUS_DONE = "Done";

    /** Statuses in the only order the workflow is allowed to move through. */
    public static final List<String> STATUS_FLOW =
            List.of(STATUS_NEW, STATUS_PROCESSING, STATUS_DONE);

    private final String id;
    private LocalDateTime claimDate;
    private String insuredPersonId;
    private String cardNumber;
    private LocalDateTime examDate;
    private final List<String> documents;
    private double claimAmount;
    private String status;

    /**
     * Creates a claim record. New claims always start in the New status.
     *
     * @param id              unique id in the format f-10digits
     * @param claimDate       moment the claim was filed
     * @param insuredPersonId id of the customer the claim is filed for
     * @param cardNumber      card number used for the medical event
     * @param examDate        moment the medical examination happened
     * @param claimAmount     claimed amount, must be greater than zero
     * @param status          current workflow status
     */
    public Claim(String id, LocalDateTime claimDate, String insuredPersonId, String cardNumber,
                 LocalDateTime examDate, double claimAmount, String status) {
        this.id = id;
        this.claimDate = claimDate;
        this.insuredPersonId = insuredPersonId;
        this.cardNumber = cardNumber;
        this.examDate = examDate;
        this.claimAmount = claimAmount;
        this.status = status;
        this.documents = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getClaimDate() {
        return claimDate;
    }

    public void setClaimDate(LocalDateTime claimDate) {
        this.claimDate = claimDate;
    }

    public String getInsuredPersonId() {
        return insuredPersonId;
    }

    public void setInsuredPersonId(String insuredPersonId) {
        this.insuredPersonId = insuredPersonId;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public LocalDateTime getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDateTime examDate) {
        this.examDate = examDate;
    }

    public double getClaimAmount() {
        return claimAmount;
    }

    public void setClaimAmount(double claimAmount) {
        this.claimAmount = claimAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * @return a read-only view of the attached document file names
     */
    public List<String> getDocuments() {
        return Collections.unmodifiableList(documents);
    }

    /**
     * Attaches a document file name. The name is expected to be validated by the
     * caller before this method is used.
     *
     * @param documentName file name to attach
     */
    public void addDocument(String documentName) {
        documents.add(documentName);
    }

    /**
     * @param documentName file name to look for
     * @return true when the claim already carries a document with that name
     */
    public boolean hasDocument(String documentName) {
        return documents.contains(documentName);
    }

    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        return String.format("%-14s | insured: %-10s | card: %-12s | exam: %s | claimed: %s"
                        + " | amount: %,.2f | status: %-10s | documents: %d",
                id, insuredPersonId, cardNumber,
                examDate.format(formatter), claimDate.format(formatter),
                claimAmount, status, documents.size());
    }

    /**
     * @return a multi line view used by the detail screens of the console menu
     */
    public String toDetailedString() {
        StringBuilder builder = new StringBuilder(toString());
        if (documents.isEmpty()) {
            builder.append(System.lineSeparator()).append("      (no documents attached)");
        } else {
            for (String document : documents) {
                builder.append(System.lineSeparator()).append("      - ").append(document);
            }
        }
        return builder.toString();
    }
}
