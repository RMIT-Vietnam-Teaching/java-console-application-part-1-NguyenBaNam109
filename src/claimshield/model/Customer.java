package claimshield.model;

/**
 * @author Nguyen Ba Nam S3974998
 */

/**
 * Represents an individual person tracked by the ClaimShield system.
 * A customer is either a policy holder (who pays for a plan) or a dependent
 * attached to exactly one policy holder.
 *
 * This class is a pure data entity: it stores state and guards its own fields,
 * but it performs no persistence and no cross-record validation. Those
 * responsibilities belong to the io and manager layers respectively.
 */
public class Customer {

    /** Value stored in customerType for a primary policy holder. */
    public static final String TYPE_POLICY_HOLDER = "PolicyHolder";

    /** Value stored in customerType for a dependent of a policy holder. */
    public static final String TYPE_DEPENDENT = "Dependent";

    private final String id;
    private String fullName;
    private String customerType;
    private String parentPolicyHolderId;

    /**
     * Creates a customer record.
     *
     * @param id                   unique id in the format c-7digits
     * @param fullName             display name of the customer
     * @param customerType         either PolicyHolder or Dependent
     * @param parentPolicyHolderId id of the paying policy holder, or null when
     *                             this customer is the policy holder themselves
     */
    public Customer(String id, String fullName, String customerType, String parentPolicyHolderId) {
        this.id = id;
        this.fullName = fullName;
        this.customerType = customerType;
        this.parentPolicyHolderId = parentPolicyHolderId;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getParentPolicyHolderId() {
        return parentPolicyHolderId;
    }

    public void setParentPolicyHolderId(String parentPolicyHolderId) {
        this.parentPolicyHolderId = parentPolicyHolderId;
    }

    /**
     * @return true when this customer is a primary policy holder
     */
    public boolean isPolicyHolder() {
        return TYPE_POLICY_HOLDER.equals(customerType);
    }

    @Override
    public String toString() {
        String parent = (parentPolicyHolderId == null) ? "-" : parentPolicyHolderId;
        return String.format("%-10s | %-24s | %-12s | policy owner: %s",
                id, fullName, customerType, parent);
    }
}
