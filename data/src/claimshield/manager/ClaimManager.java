package claimshield.manager;

/**
 * @author Nguyen Ba Nam S3974998
 */

import claimshield.model.Claim;
import claimshield.model.Customer;
import claimshield.model.InsuranceCard;
import claimshield.util.IdGenerator;
import claimshield.util.Validator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The single in-memory database of the system. It owns the three collections of
 * records and is the only place allowed to change them, so every rule about
 * uniqueness, references between records and the claim workflow is enforced in
 * exactly one layer.
 *
 * Every operation that cannot be completed throws an IllegalArgumentException
 * carrying a message written for an administrator. The console layer prints that
 * message; the file loader turns it into a skipped-line warning. Neither of them
 * needs to know the rules themselves.
 */
public class ClaimManager {

    private final List<Customer> customers;
    private final List<InsuranceCard> insuranceCards;
    private final List<Claim> claims;

    /** Creates an empty system ready to be populated from the data files. */
    public ClaimManager() {
        this.customers = new ArrayList<>();
        this.insuranceCards = new ArrayList<>();
        this.claims = new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // Customers
    // ------------------------------------------------------------------

    /**
     * Adds a customer after checking its format, its uniqueness and the policy
     * holder it points at.
     *
     * @param customer record to insert
     * @throws IllegalArgumentException when any rule is broken
     */
    public void addCustomer(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer must not be empty.");
        }
        if (!Validator.isValidCustomerId(customer.getId())) {
            throw new IllegalArgumentException(
                    "Customer id must start with c- and be exactly 9 characters, for example c-1234567.");
        }
        if (getCustomerById(customer.getId()) != null) {
            throw new IllegalArgumentException("Customer id " + customer.getId() + " is already in use.");
        }
        if (!Validator.isValidFullName(customer.getFullName())) {
            throw new IllegalArgumentException("Full name must not be empty.");
        }
        if (!Validator.isStorableText(customer.getFullName())) {
            throw new IllegalArgumentException(
                    "Full name must not contain the | character, it separates the fields of the data files.");
        }
        if (!Validator.isValidCustomerType(customer.getCustomerType())) {
            throw new IllegalArgumentException("Customer type must be PolicyHolder or Dependent.");
        }
        validateParentReference(customer.getCustomerType(), customer.getParentPolicyHolderId());
        customer.setFullName(customer.getFullName().trim());
        customers.add(customer);
    }

    /**
     * @param id customer id to look for
     * @return the matching customer, or null when no customer carries that id
     */
    public Customer getCustomerById(String id) {
        for (Customer customer : customers) {
            if (customer.getId().equals(id)) {
                return customer;
            }
        }
        return null;
    }

    /**
     * @return a read-only view of every customer currently tracked
     */
    public List<Customer> getAllCustomers() {
        return Collections.unmodifiableList(customers);
    }

    /**
     * Updates the editable fields of a customer. The id itself is immutable
     * because cards and claims refer to it.
     *
     * @param id             id of the customer to change
     * @param fullName       new display name
     * @param customerType   new customer type
     * @param parentHolderId new policy holder id, or null for a policy holder
     * @throws IllegalArgumentException when the customer is missing or a rule is broken
     */
    public void updateCustomer(String id, String fullName, String customerType, String parentHolderId) {
        Customer customer = requireCustomer(id);
        if (!Validator.isValidFullName(fullName)) {
            throw new IllegalArgumentException("Full name must not be empty.");
        }
        if (!Validator.isStorableText(fullName)) {
            throw new IllegalArgumentException(
                    "Full name must not contain the | character, it separates the fields of the data files.");
        }
        if (!Validator.isValidCustomerType(customerType)) {
            throw new IllegalArgumentException("Customer type must be PolicyHolder or Dependent.");
        }
        if (id.equals(parentHolderId)) {
            throw new IllegalArgumentException("A customer cannot be their own policy holder.");
        }
        validateParentReference(customerType, parentHolderId);
        InsuranceCard heldCard = getCardByHolderId(id);
        String expectedOwner = Customer.TYPE_POLICY_HOLDER.equals(customerType) ? id : parentHolderId;
        if (heldCard != null && !heldCard.getPolicyOwnerId().equals(expectedOwner)) {
            throw new IllegalArgumentException(
                    "Card " + heldCard.getCardNumber() + " is paid for by " + heldCard.getPolicyOwnerId()
                            + ". Remove that card before changing who covers customer " + id + ".");
        }
        if (Customer.TYPE_DEPENDENT.equals(customerType) && hasDependents(id)) {
            throw new IllegalArgumentException(
                    "Customer " + id + " still has dependents attached and must stay a PolicyHolder.");
        }
        customer.setFullName(fullName.trim());
        customer.setCustomerType(customerType);
        customer.setParentPolicyHolderId(parentHolderId);
    }

    /**
     * Removes a customer once nothing else in the system points at them.
     *
     * @param id id of the customer to remove
     * @throws IllegalArgumentException when the customer is missing or still referenced
     */
    public void deleteCustomer(String id) {
        Customer customer = requireCustomer(id);
        if (hasDependents(id)) {
            throw new IllegalArgumentException(
                    "Customer " + id + " still has dependents. Reassign or remove them first.");
        }
        for (InsuranceCard card : insuranceCards) {
            if (card.getCardHolderId().equals(id) || card.getPolicyOwnerId().equals(id)) {
                throw new IllegalArgumentException(
                        "Customer " + id + " is still linked to card " + card.getCardNumber()
                                + ". Remove that card first.");
            }
        }
        for (Claim claim : claims) {
            if (claim.getInsuredPersonId().equals(id)) {
                throw new IllegalArgumentException(
                        "Customer " + id + " still has claim " + claim.getId() + ". Remove that claim first.");
            }
        }
        customers.remove(customer);
    }

    /**
     * @return an unused customer id
     */
    public String generateCustomerId() {
        List<String> ids = new ArrayList<>();
        for (Customer customer : customers) {
            ids.add(customer.getId());
        }
        return IdGenerator.nextCustomerId(ids);
    }

    /**
     * @param policyHolderId id to test
     * @return true when at least one dependent points at that policy holder
     */
    public boolean hasDependents(String policyHolderId) {
        for (Customer customer : customers) {
            if (policyHolderId.equals(customer.getParentPolicyHolderId())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Insurance cards
    // ------------------------------------------------------------------

    /**
     * Adds an insurance card after checking its number, the customer holding it
     * and the policy holder paying for it.
     *
     * @param card record to insert
     * @throws IllegalArgumentException when any rule is broken
     */
    public void addInsuranceCard(InsuranceCard card) {
        if (card == null) {
            throw new IllegalArgumentException("Insurance card must not be empty.");
        }
        if (!Validator.isValidCardNumber(card.getCardNumber())) {
            throw new IllegalArgumentException("Card number must be exactly 10 digits.");
        }
        if (getCardByNumber(card.getCardNumber()) != null) {
            throw new IllegalArgumentException("Card number " + card.getCardNumber() + " is already in use.");
        }
        if (card.getExpirationDate() == null) {
            throw new IllegalArgumentException("Expiration date must be provided.");
        }
        Customer holder = requireCustomer(card.getCardHolderId());
        Customer owner = requireCustomer(card.getPolicyOwnerId());
        if (!owner.isPolicyHolder()) {
            throw new IllegalArgumentException("Policy owner " + owner.getId() + " must be a PolicyHolder.");
        }
        validateCardOwnership(holder, owner.getId());
        if (getCardByHolderId(holder.getId()) != null) {
            throw new IllegalArgumentException(
                    "Customer " + holder.getId() + " already holds a card.");
        }
        insuranceCards.add(card);
    }

    /**
     * @param cardNumber card number to look for
     * @return the matching card, or null when no card carries that number
     */
    public InsuranceCard getCardByNumber(String cardNumber) {
        for (InsuranceCard card : insuranceCards) {
            if (card.getCardNumber().equals(cardNumber)) {
                return card;
            }
        }
        return null;
    }

    /**
     * @param customerId id of the card holder
     * @return the card carried by that customer, or null when they hold none
     */
    public InsuranceCard getCardByHolderId(String customerId) {
        for (InsuranceCard card : insuranceCards) {
            if (card.getCardHolderId().equals(customerId)) {
                return card;
            }
        }
        return null;
    }

    /**
     * @return a read-only view of every insurance card currently tracked
     */
    public List<InsuranceCard> getAllInsuranceCards() {
        return Collections.unmodifiableList(insuranceCards);
    }

    /**
     * Moves the expiry of a card. Claims already filed against the card are
     * re-checked so the stored data cannot become contradictory.
     *
     * @param cardNumber        card to change
     * @param newExpirationDate new expiry moment
     * @throws IllegalArgumentException when the card is missing or a claim would break
     */
    public void updateCardExpiration(String cardNumber, LocalDateTime newExpirationDate) {
        InsuranceCard card = requireCard(cardNumber);
        if (newExpirationDate == null) {
            throw new IllegalArgumentException("Expiration date must be provided.");
        }
        for (Claim claim : claims) {
            if (claim.getCardNumber().equals(cardNumber)
                    && !Validator.isExamBeforeCardExpiry(claim.getExamDate(), newExpirationDate)) {
                throw new IllegalArgumentException(
                        "Claim " + claim.getId() + " has an exam date after the new expiry. Change rejected.");
            }
        }
        card.setExpirationDate(newExpirationDate);
    }

    /**
     * Removes a card once no claim refers to it.
     *
     * @param cardNumber card to remove
     * @throws IllegalArgumentException when the card is missing or still referenced
     */
    public void deleteInsuranceCard(String cardNumber) {
        InsuranceCard card = requireCard(cardNumber);
        for (Claim claim : claims) {
            if (claim.getCardNumber().equals(cardNumber)) {
                throw new IllegalArgumentException(
                        "Card " + cardNumber + " is still used by claim " + claim.getId()
                                + ". Remove that claim first.");
            }
        }
        insuranceCards.remove(card);
    }

    /**
     * @return an unused 10-digit card number
     */
    public String generateCardNumber() {
        List<String> numbers = new ArrayList<>();
        for (InsuranceCard card : insuranceCards) {
            numbers.add(card.getCardNumber());
        }
        return IdGenerator.nextCardNumber(numbers);
    }

    // ------------------------------------------------------------------
    // Claims
    // ------------------------------------------------------------------

    /**
     * Adds a claim after checking its id, its amount, the customer and card it
     * points at, and the ordering of the examination and claim dates.
     *
     * @param claim record to insert
     * @throws IllegalArgumentException when any rule is broken
     */
    public void addClaim(Claim claim) {
        if (claim == null) {
            throw new IllegalArgumentException("Claim must not be empty.");
        }
        if (!Validator.isValidClaimId(claim.getId())) {
            throw new IllegalArgumentException(
                    "Claim id must start with f- and be exactly 12 characters, for example f-1234567890.");
        }
        if (getClaimById(claim.getId()) != null) {
            throw new IllegalArgumentException("Claim id " + claim.getId() + " is already in use.");
        }
        if (!Validator.isValidClaimAmount(claim.getClaimAmount())) {
            throw new IllegalArgumentException("Claim amount must be greater than zero.");
        }
        if (!Validator.isValidStatus(claim.getStatus())) {
            throw new IllegalArgumentException("Status must be New, Processing or Done.");
        }
        requireCustomer(claim.getInsuredPersonId());
        InsuranceCard card = requireCard(claim.getCardNumber());
        if (!card.getCardHolderId().equals(claim.getInsuredPersonId())) {
            throw new IllegalArgumentException(
                    "Card " + card.getCardNumber() + " belongs to " + card.getCardHolderId()
                            + ", not to " + claim.getInsuredPersonId() + ".");
        }
        if (!Validator.isExamBeforeOrSameDayAsClaim(claim.getExamDate(), claim.getClaimDate())) {
            throw new IllegalArgumentException(
                    "Exam date must be before, or on the same day as, the claim date.");
        }
        if (!Validator.isExamBeforeCardExpiry(claim.getExamDate(), card.getExpirationDate())) {
            throw new IllegalArgumentException(
                    "Exam date must fall before the card expiry (" + card.getExpirationDate() + ").");
        }
        claims.add(claim);
    }

    /**
     * @param id claim id to look for
     * @return the matching claim, or null when no claim carries that id
     */
    public Claim getClaimById(String id) {
        for (Claim claim : claims) {
            if (claim.getId().equals(id)) {
                return claim;
            }
        }
        return null;
    }

    /**
     * @return a read-only view of every claim currently tracked
     */
    public List<Claim> getAllClaims() {
        return Collections.unmodifiableList(claims);
    }

    /**
     * @param customerId id of the insured person
     * @return every claim filed for that customer
     */
    public List<Claim> getClaimsByCustomer(String customerId) {
        List<Claim> matches = new ArrayList<>();
        for (Claim claim : claims) {
            if (claim.getInsuredPersonId().equals(customerId)) {
                matches.add(claim);
            }
        }
        return matches;
    }

    /**
     * Attaches a document file name to a claim.
     *
     * @param claimId      claim to attach the document to
     * @param documentName file name following ClaimId_CardNumber_DocumentName.pdf
     * @throws IllegalArgumentException when the claim is missing, the name is
     *                                  malformed or the same name is already
     *                                  attached
     */
    public void addDocumentToClaim(String claimId, String documentName) {
        Claim claim = requireClaim(claimId);
        String trimmed = documentName == null ? "" : documentName.trim();
        if (!Validator.isValidDocumentName(trimmed, claim.getId(), claim.getCardNumber())) {
            throw new IllegalArgumentException(
                    "Document must be named " + claim.getId() + "_" + claim.getCardNumber()
                            + "_DocumentName.pdf");
        }
        if (claim.hasDocument(trimmed)) {
            throw new IllegalArgumentException("Document " + trimmed + " is already attached to this claim.");
        }
        claim.addDocument(trimmed);
    }

    /**
     * Moves a claim one step forward through the workflow.
     *
     * @param claimId   claim to update
     * @param newStatus New, Processing or Done
     * @throws IllegalArgumentException when the claim is missing or the move is
     *                                  not a single step forward
     */
    public void updateClaimStatus(String claimId, String newStatus) {
        Claim claim = requireClaim(claimId);
        if (!Validator.isValidStatus(newStatus)) {
            throw new IllegalArgumentException("Status must be New, Processing or Done.");
        }
        if (!Validator.isForwardStatusTransition(claim.getStatus(), newStatus)) {
            throw new IllegalArgumentException(
                    "Status can only move forward one step: New to Processing, then Processing to Done. Claim "
                            + claimId + " is currently " + claim.getStatus() + ".");
        }
        claim.setStatus(newStatus);
    }

    /**
     * Changes the claimed amount of an existing claim.
     *
     * @param claimId   claim to update
     * @param newAmount new claimed amount, greater than zero
     * @throws IllegalArgumentException when the claim is missing or the amount is invalid
     */
    public void updateClaimAmount(String claimId, double newAmount) {
        Claim claim = requireClaim(claimId);
        if (!Validator.isValidClaimAmount(newAmount)) {
            throw new IllegalArgumentException("Claim amount must be greater than zero.");
        }
        claim.setClaimAmount(newAmount);
    }

    /**
     * Removes a claim from the system.
     *
     * @param claimId claim to remove
     * @throws IllegalArgumentException when no claim carries that id
     */
    public void deleteClaim(String claimId) {
        claims.remove(requireClaim(claimId));
    }

    /**
     * Re-inserts a claim exactly as it was stored on disk, together with the
     * documents it already carried.
     *
     * This is the entry point used by the persistence layer only. It applies the
     * same structural checks as {@link #addClaim(Claim)} and the same document
     * naming rule; if any stored document is invalid the whole claim is rejected
     * again, so a half valid line can never load.
     *
     * @param claim     claim rebuilt from a data file
     * @param documents document file names read from the same line
     * @throws IllegalArgumentException when the stored record breaks a rule
     */
    public void restoreClaim(Claim claim, List<String> documents) {
        addClaim(claim);
        for (String document : documents) {
            if (!Validator.isValidDocumentName(document, claim.getId(), claim.getCardNumber())) {
                claims.remove(claim);
                throw new IllegalArgumentException(
                        "Document " + document + " does not follow ClaimId_CardNumber_DocumentName.pdf");
            }
            if (claim.hasDocument(document)) {
                claims.remove(claim);
                throw new IllegalArgumentException("Document " + document + " is listed twice.");
            }
            claim.addDocument(document);
        }
    }

    /**
     * @return an unused claim id
     */
    public String generateClaimId() {
        List<String> ids = new ArrayList<>();
        for (Claim claim : claims) {
            ids.add(claim.getId());
        }
        return IdGenerator.nextClaimId(ids);
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * @param id customer id that must exist
     * @return the matching customer
     * @throws IllegalArgumentException when the customer is unknown
     */
    private Customer requireCustomer(String id) {
        Customer customer = getCustomerById(id);
        if (customer == null) {
            throw new IllegalArgumentException("No customer found with id " + id + ".");
        }
        return customer;
    }

    /**
     * @param cardNumber card number that must exist
     * @return the matching card
     * @throws IllegalArgumentException when the card is unknown
     */
    private InsuranceCard requireCard(String cardNumber) {
        InsuranceCard card = getCardByNumber(cardNumber);
        if (card == null) {
            throw new IllegalArgumentException("No insurance card found with number " + cardNumber + ".");
        }
        return card;
    }

    /**
     * @param claimId claim id that must exist
     * @return the matching claim
     * @throws IllegalArgumentException when the claim is unknown
     */
    private Claim requireClaim(String claimId) {
        Claim claim = getClaimById(claimId);
        if (claim == null) {
            throw new IllegalArgumentException("No claim found with id " + claimId + ".");
        }
        return claim;
    }

    /**
     * A policy holder must not point at a parent; a dependent must point at an
     * existing policy holder.
     *
     * @param customerType   type being applied
     * @param parentHolderId parent id being applied
     */
    private void validateParentReference(String customerType, String parentHolderId) {
        if (Customer.TYPE_POLICY_HOLDER.equals(customerType)) {
            if (parentHolderId != null) {
                throw new IllegalArgumentException(
                        "A PolicyHolder pays for their own plan and must not reference another policy holder.");
            }
            return;
        }
        if (parentHolderId == null) {
            throw new IllegalArgumentException("A Dependent must reference the id of their policy holder.");
        }
        Customer parent = getCustomerById(parentHolderId);
        if (parent == null) {
            throw new IllegalArgumentException("No policy holder found with id " + parentHolderId + ".");
        }
        if (!parent.isPolicyHolder()) {
            throw new IllegalArgumentException("Customer " + parentHolderId + " is not a PolicyHolder.");
        }
    }

    /**
     * A card must be paid for by the holder themselves when the holder is a
     * policy holder, or by the holder's policy holder when they are a dependent.
     *
     * @param holder        customer carrying the card
     * @param policyOwnerId id recorded as the payer
     */
    private void validateCardOwnership(Customer holder, String policyOwnerId) {
        if (holder.isPolicyHolder()) {
            if (!holder.getId().equals(policyOwnerId)) {
                throw new IllegalArgumentException(
                        "A PolicyHolder card must record the holder themselves as the policy owner.");
            }
            return;
        }
        if (!policyOwnerId.equals(holder.getParentPolicyHolderId())) {
            throw new IllegalArgumentException(
                    "Dependent " + holder.getId() + " is covered by " + holder.getParentPolicyHolderId()
                            + ", so the policy owner must be that customer.");
        }
    }
}
