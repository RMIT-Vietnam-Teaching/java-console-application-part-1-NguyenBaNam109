package claimshield.model;

/**
 * @author Nguyen Ba Nam S3974998
 */

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents the insurance coverage card issued to a customer.
 * The card links the person who uses it (cardHolderId) to the person who pays
 * for the plan (policyOwnerId); for a policy holder both ids are the same.
 */
public class InsuranceCard {

    private final String cardNumber;
    private String cardHolderId;
    private String policyOwnerId;
    private LocalDateTime expirationDate;

    /**
     * Creates an insurance card record.
     *
     * @param cardNumber     unique 10-digit card number
     * @param cardHolderId   id of the customer carrying the card
     * @param policyOwnerId  id of the policy holder paying for the plan
     * @param expirationDate moment the coverage stops being valid
     */
    public InsuranceCard(String cardNumber, String cardHolderId, String policyOwnerId,
                         LocalDateTime expirationDate) {
        this.cardNumber = cardNumber;
        this.cardHolderId = cardHolderId;
        this.policyOwnerId = policyOwnerId;
        this.expirationDate = expirationDate;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getCardHolderId() {
        return cardHolderId;
    }

    public void setCardHolderId(String cardHolderId) {
        this.cardHolderId = cardHolderId;
    }

    public String getPolicyOwnerId() {
        return policyOwnerId;
    }

    public void setPolicyOwnerId(String policyOwnerId) {
        this.policyOwnerId = policyOwnerId;
    }

    public LocalDateTime getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDateTime expirationDate) {
        this.expirationDate = expirationDate;
    }

    /**
     * @param moment point in time to test against
     * @return true when the card is still valid at the given moment
     */
    public boolean isValidAt(LocalDateTime moment) {
        return moment.isBefore(expirationDate);
    }

    @Override
    public String toString() {
        return String.format("%-12s | holder: %-10s | policy owner: %-10s | expires: %s",
                cardNumber, cardHolderId, policyOwnerId,
                expirationDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
