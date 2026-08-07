package claimshield.util;

/**
 * @author Nguyen Ba Nam S3974998
 */

import claimshield.model.Claim;
import claimshield.model.Customer;

import java.time.LocalDateTime;

/**
 * Central home for every structural rule of the system: id shapes, card number
 * shape, document file name shape and the claim status workflow.
 *
 * Keeping these checks in one place means the console layer, the manager layer
 * and the file loader all judge a value by exactly the same rule, so a record
 * typed by an administrator and a record read from disk can never disagree.
 */
public final class Validator {

    /** Customer id: the literal c- followed by 7 digits, 9 characters in total. */
    public static final String CUSTOMER_ID_REGEX = "c-\\d{7}";

    /** Claim id: the literal f- followed by 10 digits, 12 characters in total. */
    public static final String CLAIM_ID_REGEX = "f-\\d{10}";

    /** Card number: exactly 10 digits. */
    public static final String CARD_NUMBER_REGEX = "\\d{10}";

    /** Utility class, never instantiated. */
    private Validator() {
    }

    public static boolean isValidCustomerId(String id) {
        return id != null && id.matches(CUSTOMER_ID_REGEX);
    }

    public static boolean isValidClaimId(String id) {
        return id != null && id.matches(CLAIM_ID_REGEX);
    }

    public static boolean isValidCardNumber(String cardNumber) {
        return cardNumber != null && cardNumber.matches(CARD_NUMBER_REGEX);
    }

    public static boolean isValidCustomerType(String customerType) {
        return Customer.TYPE_POLICY_HOLDER.equals(customerType)
                || Customer.TYPE_DEPENDENT.equals(customerType);
    }

    public static boolean isValidStatus(String status) {
        return Claim.STATUS_NEW.equals(status)
                || Claim.STATUS_PROCESSING.equals(status)
                || Claim.STATUS_DONE.equals(status);
    }

    public static boolean isValidFullName(String fullName) {
        return fullName != null && !fullName.trim().isEmpty();
    }

    /**
     * @param text value typed for a free text field such as a name
     * @return true when the text can be stored in the pipe separated data files
     */
    public static boolean isStorableText(String text) {
        return text != null && !text.contains("|");
    }

    public static boolean isValidClaimAmount(double amount) {
        return amount > 0;
    }

    /**
     * The examination must happen before the claim is filed, or at the latest on
     * the same calendar day.
     *
     * @param examDate  moment of the medical examination
     * @param claimDate moment the claim was filed
     * @return true when the pair of dates respects the rule
     */
    public static boolean isExamBeforeOrSameDayAsClaim(LocalDateTime examDate, LocalDateTime claimDate) {
        if (examDate == null || claimDate == null) {
            return false;
        }
        return !examDate.toLocalDate().isAfter(claimDate.toLocalDate());
    }

    /**
     * @param examDate       moment of the medical examination
     * @param expirationDate moment the card stops being valid
     * @return true when the examination happened while the card was still valid
     */
    public static boolean isExamBeforeCardExpiry(LocalDateTime examDate, LocalDateTime expirationDate) {
        if (examDate == null || expirationDate == null) {
            return false;
        }
        return examDate.isBefore(expirationDate);
    }

    /**
     * Checks the mandatory document naming convention
     * ClaimId_CardNumber_DocumentName.pdf.
     *
     * @param documentName file name proposed by the administrator
     * @param claimId      id of the claim the document belongs to
     * @param cardNumber   card number recorded on that claim
     * @return true when the file name follows the convention exactly
     */
    public static boolean isValidDocumentName(String documentName, String claimId, String cardNumber) {
        if (documentName == null || !documentName.endsWith(".pdf")) {
            return false;
        }
        String requiredPrefix = claimId + "_" + cardNumber + "_";
        if (!documentName.startsWith(requiredPrefix)) {
            return false;
        }
        String descriptivePart = documentName.substring(requiredPrefix.length(),
                documentName.length() - ".pdf".length());
        return !descriptivePart.isEmpty()
                && !descriptivePart.contains("|")
                && !descriptivePart.contains(";");
    }

    /**
     * A claim may only step forward through New, Processing and Done. Every
     * other move, including staying put or jumping over a stage, is rejected.
     *
     * @param currentStatus status stored on the claim right now
     * @param newStatus     status the administrator wants to apply
     * @return true when the transition is a single step forward
     */
    public static boolean isForwardStatusTransition(String currentStatus, String newStatus) {
        int currentIndex = statusIndex(currentStatus);
        int newIndex = statusIndex(newStatus);
        if (currentIndex < 0 || newIndex < 0) {
            return false;
        }
        return newIndex == currentIndex + 1;
    }

    /**
     * @param status status label to locate
     * @return position of the status inside the workflow, or -1 when unknown
     */
    private static int statusIndex(String status) {
        return Claim.STATUS_FLOW.indexOf(status);
    }
}
