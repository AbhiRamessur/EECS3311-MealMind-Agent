/**
 * Deterministic tools exposed to the agent (pantry, constraint validation, recipe search, shopping list).
 * Every tool is read-only and is called through {@link com.mealmind.tools.ToolManager}, which validates
 * the JSON arguments against each tool's declared {@link com.mealmind.tools.ToolSpec}.
 */
package com.mealmind.tools;
