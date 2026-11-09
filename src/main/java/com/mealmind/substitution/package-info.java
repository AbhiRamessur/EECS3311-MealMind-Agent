/**
 * Ingredient substitution (F07): the request and option types, {@link com.mealmind.substitution.IngredientLocator}
 * (finds the ingredient a user names) and {@link com.mealmind.substitution.SubstitutionApplier}, which re-checks
 * a chosen option's safety and applies it through an undoable
 * {@link com.mealmind.command.SubstituteIngredientCommand}. The AI part (suggesting options) lives in the agent
 * package behind {@code AgentFacade.suggestSubstitutions}.
 */
package com.mealmind.substitution;
