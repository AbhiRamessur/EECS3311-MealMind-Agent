package com.mealmind.gui.logic;

import com.mealmind.substitution.SubstituteText;
import com.mealmind.substitution.SubstitutionOption;
import com.mealmind.substitution.SubstitutionProposal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a {@link SubstitutionProposal} into table rows. Every text comes from {@link SubstituteText}, so the
 * pieces equal what the CLI's {@code recipe substitute} prints.
 */
public final class SubstituteRows {

    private SubstituteRows() {
    }

    /**
     * Builds one row per option, best first.
     *
     * @param proposal the proposal
     * @return the rows
     * @throws NullPointerException if {@code proposal} is null
     */
    public static List<SubstituteRow> of(SubstitutionProposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        List<SubstituteRow> rows = new ArrayList<>();
        for (int i = 0; i < proposal.options().size(); i++) {
            SubstitutionOption option = proposal.options().get(i);
            rows.add(new SubstituteRow(String.valueOf(i + 1), SubstituteText.optionText(proposal, i), option.note(),
                    option.inPantry() ? SubstituteText.IN_PANTRY : "", SubstituteText.nutritionText(option)));
        }
        return rows;
    }
}
