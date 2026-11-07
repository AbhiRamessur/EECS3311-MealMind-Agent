/**
 * The GUI's presentation layer without any JavaFX dependency: form parsing, table rows, undo/redo labels,
 * the AI status text and observer bindings. Everything here is plain Java and unit-tested without a
 * display, and it reuses the backend's own validation and the CLI's formatting so both front ends agree.
 */
package com.mealmind.gui.logic;
