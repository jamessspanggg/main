package seedu.address.model;

import static java.util.Objects.requireNonNull;
import static seedu.address.commons.util.CollectionUtil.requireAllNonNull;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import seedu.address.commons.core.GuiSettings;
import seedu.address.commons.core.LogsCenter;
import seedu.address.model.expense.Expense;
import seedu.address.model.person.exceptions.ExpenseNotFoundException;

/**
 * Represents the in-memory model of the address book data.
 */
public class ModelManager implements Model {
    private static final Logger logger = LogsCenter.getLogger(ModelManager.class);

    private final VersionedFinanceTracker versionedFinanceTracker;
    private final UserPrefs userPrefs;
    private final FilteredList<Expense> filteredExpenses;
    private final SimpleObjectProperty<Expense> selectedExpense = new SimpleObjectProperty<>();

    /**
     * Initializes a ModelManager with the given financeTracker and userPrefs.
     */
    public ModelManager(ReadOnlyFinanceTracker financeTracker, ReadOnlyUserPrefs userPrefs) {
        super();
        requireAllNonNull(financeTracker, userPrefs);

        logger.fine("Initializing with finance tracker: " + financeTracker + " and user prefs " + userPrefs);

        versionedFinanceTracker = new VersionedFinanceTracker(financeTracker);
        this.userPrefs = new UserPrefs(userPrefs);
        filteredExpenses = new FilteredList<>(versionedFinanceTracker.getFinanceList());
        filteredExpenses.addListener(this::ensureSelectedExpenseIsValid);
    }

    public ModelManager() {
        this(new FinanceTracker(), new UserPrefs());
    }

    //=========== UserPrefs ==================================================================================

    @Override
    public void setUserPrefs(ReadOnlyUserPrefs userPrefs) {
        requireNonNull(userPrefs);
        this.userPrefs.resetData(userPrefs);
    }

    @Override
    public ReadOnlyUserPrefs getUserPrefs() {
        return userPrefs;
    }

    @Override
    public GuiSettings getGuiSettings() {
        return userPrefs.getGuiSettings();
    }

    @Override
    public void setGuiSettings(GuiSettings guiSettings) {
        requireNonNull(guiSettings);
        userPrefs.setGuiSettings(guiSettings);
    }

    @Override
    public Path getFinanceTrackerFilePath() {
        return userPrefs.getFinanceTrackerFilePath();
    }

    @Override
    public void setFinanceTrackerFilePath(Path financeTrackerFilePath) {
        requireNonNull(financeTrackerFilePath);
        userPrefs.setFinanceTrackerFilePath(financeTrackerFilePath);
    }

    //=========== FinanceTracker ================================================================================

    @Override
    public void setFinanceTracker(ReadOnlyFinanceTracker financeTracker) {
        versionedFinanceTracker.resetData(financeTracker);
    }

    @Override
    public ReadOnlyFinanceTracker getFinanceTracker() {
        return versionedFinanceTracker;
    }

    @Override
    public boolean hasExpense(Expense expense) {
        requireNonNull(expense);
        return versionedFinanceTracker.hasExpense(expense);
    }

    @Override
    public void deleteExpense(Expense target) {
        versionedFinanceTracker.removeExpense(target);
    }

    @Override
    public void addExpense(Expense expense) {
        versionedFinanceTracker.addExpense(expense);
        updateFilteredExpenseList(PREDICATE_SHOW_ALL_FINANCES);
    }

    @Override
    public void setExpense(Expense target, Expense editedExpense) {
        requireAllNonNull(target, editedExpense);

        versionedFinanceTracker.setExpense(target, editedExpense);
    }

    //=========== Filtered Expense List Accessors =============================================================

    /**
     * Returns an unmodifiable view of the list of {@code Expense} backed by the internal list of
     * {@code versionedFinanceTracker}
     */
    @Override
    public ObservableList<Expense> getFilteredExpenseList() {
        return filteredExpenses;
    }

    @Override
    public void updateFilteredExpenseList(Predicate<Expense> predicate) {
        requireNonNull(predicate);
        filteredExpenses.setPredicate(predicate);
    }

    //=========== Undo/Redo =================================================================================

    @Override
    public boolean canUndoFinanceTracker() {
        return versionedFinanceTracker.canUndo();
    }

    @Override
    public boolean canRedoFinanceTracker() {
        return versionedFinanceTracker.canRedo();
    }

    @Override
    public void undoFinanceTracker() {
        versionedFinanceTracker.undo();
    }

    @Override
    public void redoFinanceTracker() {
        versionedFinanceTracker.redo();
    }

    @Override
    public void commitFinanceTracker() {
        versionedFinanceTracker.commit();
    }

    //=========== Selected expense ===========================================================================

    @Override
    public ReadOnlyProperty<Expense> selectedExpenseProperty() {
        return selectedExpense;
    }

    @Override
    public Expense getSelectedExpense() {
        return selectedExpense.getValue();
    }

    @Override
    public void setSelectedExpense(Expense expense) {
        if (expense != null && !filteredExpenses.contains(expense)) {
            throw new ExpenseNotFoundException();
        }
        selectedExpense.setValue(expense);
    }

    /**
     * Ensures {@code selectedExpense} is a valid expense in {@code filteredExpenses}.
     */
    private void ensureSelectedExpenseIsValid(ListChangeListener.Change<? extends Expense> change) {
        while (change.next()) {
            if (selectedExpense.getValue() == null) {
                // null is always a valid selected expense, so we do not need to check that it is valid anymore.
                return;
            }

            boolean wasSelectedExpenseReplaced = change.wasReplaced() && change.getAddedSize() == change.getRemovedSize()
                    && change.getRemoved().contains(selectedExpense.getValue());
            if (wasSelectedExpenseReplaced) {
                // Update selectedExpense to its new value.
                int index = change.getRemoved().indexOf(selectedExpense.getValue());
                selectedExpense.setValue(change.getAddedSubList().get(index));
                continue;
            }

            boolean wasSelectedExpenseRemoved = change.getRemoved().stream()
                    .anyMatch(removedExpense -> selectedExpense.getValue().isSameExpense(removedExpense));
            if (wasSelectedExpenseRemoved) {
                // Select the expense that came before it in the list,
                // or clear the selection if there is no such expense.
                selectedExpense.setValue(change.getFrom() > 0 ? change.getList().get(change.getFrom() - 1) : null);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        // short circuit if same object
        if (obj == this) {
            return true;
        }

        // instanceof handles nulls
        if (!(obj instanceof ModelManager)) {
            return false;
        }

        // state check
        ModelManager other = (ModelManager) obj;
        return versionedFinanceTracker.equals(other.versionedFinanceTracker)
                && userPrefs.equals(other.userPrefs)
                && filteredExpenses.equals(other.filteredExpenses)
                && Objects.equals(selectedExpense.get(), other.selectedExpense.get());
    }

}
