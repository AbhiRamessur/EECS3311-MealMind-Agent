package com.mealmind.gui.logic;

import com.mealmind.leftovers.LeftoverFlow;
import com.mealmind.leftovers.LeftoverProposal;
import com.mealmind.leftovers.LeftoverRequest;
import java.util.List;

/**
 * The texts of the Pantry tab's "Use up expiring items" section. Every string comes from {@link LeftoverFlow},
 * which the CLI's {@code plan leftovers} prints too, so the two front ends use the same wording.
 */
public final class LeftoverView {

    private LeftoverView() {
    }

    /**
     * The lines above the table: "Expiring within N days: ..." and one "Note: ..." line per note.
     *
     * @param proposal   the proposal
     * @param request    the request that produced it
     * @param extraNotes notes from the flow outcome (e.g. the AI note)
     * @return the lines, as the CLI prints them
     */
    public static List<String> headerLines(LeftoverProposal proposal, LeftoverRequest request, List<String> extraNotes) {
        return LeftoverFlow.headerLines(proposal, request, extraNotes);
    }

    /**
     * The title above the proposed meals.
     *
     * @return "Proposal (nothing applied yet):"
     */
    public static String proposalTitle() {
        return LeftoverFlow.PROPOSAL_TITLE;
    }

    /**
     * The message after a successful apply.
     *
     * @param commandDescription the command's description
     * @return "Applied: &lt;description&gt;."
     */
    public static String applyMessage(String commandDescription) {
        return LeftoverFlow.appliedMessage(commandDescription);
    }

    /**
     * The undo hint after a successful apply.
     *
     * @return "Use 'undo' to revert."
     */
    public static String applyHint() {
        return LeftoverFlow.UNDO_HINT;
    }

    /**
     * The message for an AI failure; the deterministic proposal stays.
     *
     * @param problem the AI problem
     * @return "AI explanation unavailable: &lt;problem&gt;"
     */
    public static String aiProblem(String problem) {
        return LeftoverFlow.AI_PROBLEM_PREFIX + problem;
    }

    /**
     * The hint about how many pantry items expire soon.
     *
     * @param count the number of items expiring within the window (expired ones included)
     * @param days  the window in days
     * @return e.g. "2 items expire within 3 days"
     */
    public static String expiringHint(int count, int days) {
        return count + (count == 1 ? " item expires" : " items expire") + " within " + days + (days == 1 ? " day" : " days");
    }
}
