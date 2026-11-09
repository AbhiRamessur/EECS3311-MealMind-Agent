package com.mealmind.gui.logic;

import com.mealmind.leftovers.LeftoverProposal;
import com.mealmind.leftovers.LeftoverRequest;
import com.mealmind.model.MealPlan;
import java.util.Objects;
import java.util.Optional;

/**
 * The pending leftover proposal of the Pantry tab and the request that produced it (the GUI counterpart of
 * the CLI's pending proposal). Plain state, testable without a toolkit; used on the UI thread only.
 */
public final class LeftoverSession {

    private LeftoverProposal proposal;
    private LeftoverRequest request;

    /**
     * Makes {@code proposal} the pending one, replacing any earlier one.
     *
     * @param proposal the proposal
     * @param request  the request that produced it
     * @throws NullPointerException if an argument is null
     */
    public void set(LeftoverProposal proposal, LeftoverRequest request) {
        this.proposal = Objects.requireNonNull(proposal, "proposal");
        this.request = Objects.requireNonNull(request, "request");
    }

    /** Forgets the pending proposal. */
    public void clear() {
        proposal = null;
        request = null;
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
    public Optional<LeftoverProposal> proposal() {
        return Optional.ofNullable(proposal);
    }

    /**
     * Returns the request that produced the pending proposal.
     *
     * @return the request, or empty
     */
    public Optional<LeftoverRequest> request() {
        return Optional.ofNullable(request);
    }

    /**
     * Returns whether the pending proposal was made for another plan object than {@code current} (the plan was
     * replaced). Used after the planner refused an apply, to decide whether to drop the proposal, as the CLI does.
     *
     * @param current the plan in use now
     * @return {@code true} when a proposal is pending and was made for a different plan
     */
    public boolean isStaleFor(MealPlan current) {
        return proposal != null && proposal.plan() != current;
    }
}
