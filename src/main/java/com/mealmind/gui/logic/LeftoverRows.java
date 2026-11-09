package com.mealmind.gui.logic;

import com.mealmind.cli.CLIHandler;
import com.mealmind.leftovers.LeftoverAssignment;
import com.mealmind.leftovers.LeftoverProposal;
import java.util.List;
import java.util.Objects;

/**
 * Turns a {@link LeftoverProposal} into table rows. The slot uses the Plan tab's day labels
 * ({@link PlanGridModel#dayLabel}) and the "replaces" text is the CLI's "[replaces ...]" content.
 */
public final class LeftoverRows {

    private LeftoverRows() {
    }

    /**
     * Builds one row per proposed meal, in slot order.
     *
     * @param proposal the proposal
     * @return the rows
     * @throws NullPointerException if {@code proposal} is null
     */
    public static List<LeftoverRow> of(LeftoverProposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        return proposal.assignments().stream().map(a -> row(proposal, a)).toList();
    }

    private static LeftoverRow row(LeftoverProposal proposal, LeftoverAssignment assignment) {
        String replaces = assignment.replacesExisting()
                ? proposal.plan().getMeal(assignment.day(), assignment.slot())
                        .map(meal -> "replaces " + CLIHandler.describe(meal)).orElse("")
                : "";
        return new LeftoverRow(PlanGridModel.dayLabel(assignment.day()) + " " + assignment.slot(),
                assignment.recipe().getName(), assignment.reason(), replaces);
    }
}
