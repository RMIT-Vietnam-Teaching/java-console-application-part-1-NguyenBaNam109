package claimshield.util;

/**
 * @author Nguyen Ba Nam S3974998
 */

import java.util.Collection;

/**
 * Produces the next free identifier for each record type.
 *
 * The generator never keeps its own counter. It derives the next value from the
 * ids currently in memory, which keeps generated ids correct after a restart,
 * after a deletion, and after records were edited directly in the data files.
 */
public final class IdGenerator {

    private static final String CUSTOMER_PREFIX = "c-";
    private static final String CLAIM_PREFIX = "f-";
    private static final int CUSTOMER_DIGITS = 7;
    private static final int CLAIM_DIGITS = 10;
    private static final int CARD_DIGITS = 10;

    /** Utility class, never instantiated. */
    private IdGenerator() {
    }

    /**
     * @param existingIds ids already in use
     * @return an unused id in the format c-7digits
     */
    public static String nextCustomerId(Collection<String> existingIds) {
        return nextId(existingIds, CUSTOMER_PREFIX, CUSTOMER_DIGITS);
    }

    /**
     * @param existingIds ids already in use
     * @return an unused id in the format f-10digits
     */
    public static String nextClaimId(Collection<String> existingIds) {
        return nextId(existingIds, CLAIM_PREFIX, CLAIM_DIGITS);
    }

    /**
     * @param existingCardNumbers card numbers already in use
     * @return an unused 10-digit card number
     */
    public static String nextCardNumber(Collection<String> existingCardNumbers) {
        return nextId(existingCardNumbers, "", CARD_DIGITS);
    }

    /**
     * Finds the largest numeric suffix in use and returns the next one, padded
     * back to the required width.
     *
     * @param existingIds values already in use
     * @param prefix      literal prefix of the id family
     * @param digits      number of digits after the prefix
     * @return the next unused value of that family
     */
    private static String nextId(Collection<String> existingIds, String prefix, int digits) {
        long highest = 0;
        for (String existing : existingIds) {
            if (existing == null || !existing.startsWith(prefix)) {
                continue;
            }
            String numericPart = existing.substring(prefix.length());
            if (numericPart.length() != digits) {
                continue;
            }
            try {
                highest = Math.max(highest, Long.parseLong(numericPart));
            } catch (NumberFormatException ignored) {
                // A malformed value cannot influence the next id.
            }
        }
        long limit = (long) Math.pow(10, digits);
        long candidate = highest + 1;
        if (candidate >= limit) {
            candidate = 1;
        }
        String next = prefix + String.format("%0" + digits + "d", candidate);
        while (existingIds.contains(next)) {
            candidate = (candidate + 1 >= limit) ? 1 : candidate + 1;
            next = prefix + String.format("%0" + digits + "d", candidate);
        }
        return next;
    }
}
