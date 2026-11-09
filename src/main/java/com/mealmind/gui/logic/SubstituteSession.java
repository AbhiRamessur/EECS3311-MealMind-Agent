package com.mealmind.gui.logic;

import com.mealmind.model.RecipeBook;
import com.mealmind.substitution.SubstitutionProposal;
import java.util.Objects;
import java.util.Optional;

/**
 * The pending substitution suggestion of the Recipes tab (the GUI counterpart of the CLI's pending proposal).
 * Plain state, testable without a toolkit; used on the UI thread only.
 */
public final class SubstituteSession {

    private SubstitutionProposal proposal;

    /**
     * Makes {@code proposal} the pending one, replacing any earlier one.
     *
     * @param proposal the proposal
     * @throws NullPointerException if {@code proposal} is null
     */
    public void set(SubstitutionProposal proposal) {
        this.proposal = Objects.requireNonNull(proposal, "proposal");
    }

    /** Forgets the pending proposal. */
    public void clear() {
        proposal = null;
    }

    /**
     * Returns whether a proposal is pending.
     *
     * @return {@code true} if there is one
     */
    public boolean isPending() {
        return proposal != null;
    }

    /**
     * Returns the pending proposal.
     *
     * @return the proposal, or empty
     */
    public Optional<SubstitutionProposal> proposal() {
        return Optional.ofNullable(proposal);
    }

    /**
     * Returns whether the book no longer holds the recipe instance the pending proposal was made for. Used only
     * after the applier refused an apply, to decide whether to drop the proposal, exactly as the CLI does.
     *
     * @param book the recipe book
     * @return {@code true} when a proposal is pending and its recipe was replaced or removed
     */
    public boolean isStaleIn(RecipeBook book) {
        return proposal != null && book.get(proposal.recipe().getId()).orElse(null) != proposal.recipe();
    }
}
