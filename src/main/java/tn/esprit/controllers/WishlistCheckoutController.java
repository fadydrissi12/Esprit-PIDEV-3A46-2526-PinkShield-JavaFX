package tn.esprit.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import tn.esprit.entities.Transaction;
import tn.esprit.entities.User;
import tn.esprit.entities.WishlistDisplayItem;
import tn.esprit.main.MainFX;
import tn.esprit.services.PaymentService;
import tn.esprit.services.GiphyService;
import tn.esprit.services.NotificationService;
import tn.esprit.services.TransactionService;
import tn.esprit.services.WishlistService;
import tn.esprit.services.WishlistPdfService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller for User Wishlist View with Integrated Payment Processing
 * Handles wishlist display, PDF export, and mock payment checkout with receipt generation
 */
public class WishlistCheckoutController {

    @FXML
    private TextField searchBar;
    @FXML
    private TableView<WishlistDisplayItem> wishlistTable;
    @FXML
    private TableColumn<WishlistDisplayItem, Integer> colId;
    @FXML
    private TableColumn<WishlistDisplayItem, String> colProductName;
    @FXML
    private TableColumn<WishlistDisplayItem, String> colPrice;
    @FXML
    private TableColumn<WishlistDisplayItem, String> colAddedDate;
    @FXML
    private TableColumn<WishlistDisplayItem, Void> colActions;
    @FXML
    private Label totalPriceLabel;

    // Payment form fields
    @FXML
    private TextField cardholderNameField;
    @FXML
    private TextField cardNumberField;
    @FXML
    private TextField expiryDateField;
    @FXML
    private TextField cvvField;
    @FXML
    private Label orderItemsLabel;
    @FXML
    private Label orderTotalLabel;
    @FXML
    private Label feedbackLabel;

    private final WishlistService wishlistService = new WishlistService();
    private final PaymentService paymentService = MainFX.getPaymentService();
    private final WishlistPdfService pdfService = new WishlistPdfService();
    private final TransactionService transactionService = new TransactionService();
    private final NotificationService notificationService = new NotificationService();
    private final GiphyService giphyService = new GiphyService();
    private ObservableList<WishlistDisplayItem> wishlistItems;
    private FilteredList<WishlistDisplayItem> filteredItems;
    private User currentUser;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupSearch();
    }

    public void setCurrentUser(User user) {
        currentUser = user;
        if (user != null) {
            loadWishlist();
        }
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colProductName.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colPrice.setCellValueFactory(cellData -> 
                new javafx.beans.property.SimpleStringProperty(formatPrice(cellData.getValue().getPrice())));
        colAddedDate.setCellValueFactory(cellData -> {
            java.sql.Timestamp timestamp = cellData.getValue().getAddedAt();
            String formatted = timestamp != null ? 
                    timestamp.toLocalDateTime().toLocalDate().toString() : "N/A";
            return new javafx.beans.property.SimpleStringProperty(formatted);
        });

        colActions.setCellFactory(column -> new TableCell<WishlistDisplayItem, Void>() {
            private final Button deleteBtn = new Button("Delete");
            private final Button pdfBtn = new Button("PDF");
            private final HBox container = new HBox(5);

            {
                deleteBtn.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 5 8;");
                pdfBtn.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 5 8;");

                deleteBtn.setOnAction(event -> handleDeleteItem(getTableView().getItems().get(getIndex())));
                pdfBtn.setOnAction(event -> handleDownloadPDF(getTableView().getItems().get(getIndex())));

                container.getChildren().addAll(pdfBtn, deleteBtn);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : container);
            }
        });
    }

    private void setupSearch() {
        searchBar.textProperty().addListener((obs, oldVal, newVal) -> {
            if (filteredItems != null) {
                filteredItems.setPredicate(item -> {
                    if (newVal == null || newVal.isEmpty()) return true;
                    String lower = newVal.toLowerCase();
                    return item.getProductName().toLowerCase().contains(lower) ||
                           String.valueOf(item.getId()).contains(lower);
                });
                updateTotalPrice();
            }
        });
    }

    private void loadWishlist() {
        try {
            if (currentUser == null) return;
            
            var items = wishlistService.getDisplayItemsByUser(currentUser.getId());
            wishlistItems = FXCollections.observableArrayList(items);
            filteredItems = new FilteredList<>(wishlistItems, p -> true);
            wishlistTable.setItems(filteredItems);
            updateTotalPrice();
            updateOrderSummary();
        } catch (Exception e) {
            showAlert("Error", "Could not load wishlist: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleDeleteItem(WishlistDisplayItem item) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Remove this item from wishlist?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Remove Item");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.YES) {
                try {
                    wishlistService.removeItem(item.getId(), currentUser.getId());
                    loadWishlist();
                    showAlert("Success", "Item removed from wishlist", Alert.AlertType.INFORMATION);
                } catch (Exception e) {
                    showAlert("Error", "Could not remove item: " + e.getMessage(), Alert.AlertType.ERROR);
                }
            }
        });
    }

    @FXML
    private void handleDownloadPDF(WishlistDisplayItem item) {
        if (item == null) {
            showAlert("Error", "Please select an item to download", Alert.AlertType.ERROR);
            return;
        }
        
        try {
            List<WishlistDisplayItem> singleItem = new ArrayList<>();
            singleItem.add(item);
            pdfService.exportWishlistPdf(singleItem, item.getPrice());
            showAlert("Success", "PDF downloaded successfully for: " + item.getProductName(), Alert.AlertType.INFORMATION);
        } catch (Exception e) {
            showAlert("Error", "Could not generate PDF: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handlePayment() {
        if (!validatePayment()) return;

        PaymentService.PaymentDetails details = new PaymentService.PaymentDetails(
                cardholderNameField.getText().trim(),
                cardNumberField.getText().trim(),
                expiryDateField.getText().trim(),
                cvvField.getText().trim()
        );

        for (WishlistDisplayItem item : wishlistItems) {
            details.addItem(new PaymentService.PaymentItem(
                    item.getId(),
                    item.getProductName(),
                    item.getPrice(),
                    1
            ));
        }

        if (paymentService.processPayment(details)) {
            String transactionId = paymentService.generateTransactionId();
            String receipt = paymentService.generateReceipt(transactionId, details);
            
            // Generate PDF receipt
            try {
                String cardholderName = cardholderNameField.getText().trim();
                List<WishlistDisplayItem> receiptItems = new ArrayList<>(wishlistItems);
                double totalAmount = details.getTotalAmount();
                
                String receiptFileName = pdfService.exportPaymentReceiptPdf(transactionId, cardholderName, receiptItems, totalAmount);
                String cleanedCardNumber = cardNumberField.getText().replaceAll("\\D", "");
                String cardLastFour = cleanedCardNumber.length() >= 4
                        ? cleanedCardNumber.substring(cleanedCardNumber.length() - 4)
                        : "";

                Transaction transaction = new Transaction(
                        currentUser.getId(),
                        transactionId,
                        cardholderName,
                        cardLastFour,
                        totalAmount,
                        receiptItems.size(),
                        "SUCCESSFUL",
                        receiptFileName,
                        serializeTransactionItems(receiptItems)
                );
                if (!transactionService.saveTransaction(transaction)) {
                    System.err.println("Transaction was processed but could not be saved to history.");
                }
                notificationService.notifyUser(
                        currentUser,
                        "transaction",
                        "Transaction successful",
                        "Your payment of " + formatPrice(totalAmount) + " succeeded. Transaction ID: " + transactionId + "."
                );
                notificationService.notifySymfonyUser(
                        currentUser,
                        "Transaction successful",
                        "Your payment of " + formatPrice(totalAmount) + " succeeded. Transaction ID: " + transactionId + ".",
                        "success",
                        "fas fa-credit-card"
                );
                
                // Clear forms and wishlist
                clearPaymentForm();
                clearWishlist();
                loadWishlist();
                
                // Show success with PDF generation confirmation
                showAlert("Payment Successful", 
                        paymentService.getSuccessMessage(details.getTotalAmount()) + 
                        "\n\nReceipt PDF has been downloaded to your Downloads folder.\n" +
                        "Transaction ID: " + transactionId,
                        Alert.AlertType.INFORMATION);
                showCelebrationGif();
            } catch (Exception e) {
                notificationService.notifyUser(
                        currentUser,
                        "transaction",
                        "Receipt generation warning",
                        "Your payment was processed, but the receipt could not be generated: " + e.getMessage()
                );
                showAlert("Payment Processing Error", 
                        "Payment was processed but PDF receipt could not be generated: " + e.getMessage(), 
                        Alert.AlertType.WARNING);
                // Still clear the wishlist even if PDF generation failed
                try {
                    clearWishlist();
                    loadWishlist();
                } catch (Exception ex) {
                    System.err.println("Error clearing wishlist: " + ex.getMessage());
                }
            }
        } else {
            notificationService.notifyUser(
                    currentUser,
                    "transaction",
                    "Transaction failed",
                    "Your wishlist payment was declined or could not be processed."
            );
            showAlert("Payment Failed", "Payment could not be processed. Please check your card details and try again.", Alert.AlertType.ERROR);
        }
    }

    private String serializeTransactionItems(List<WishlistDisplayItem> items) {
        StringBuilder builder = new StringBuilder();
        for (WishlistDisplayItem item : items) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(item.getProductName())
                    .append(" | ")
                    .append(formatPrice(item.getPrice()));
        }
        return builder.toString();
    }

    private boolean validatePayment() {
        if (wishlistItems == null || wishlistItems.isEmpty()) {
            showAlert("Empty Wishlist", "Please add items before checkout", Alert.AlertType.WARNING);
            return false;
        }
        
        String cardholderName = cardholderNameField.getText().trim();
        String cardNumber = cardNumberField.getText().trim();
        String expiryDate = expiryDateField.getText().trim();
        String cvv = cvvField.getText().trim();
        
        if (cardholderName.isEmpty()) {
            showAlert("Validation Error", "Please enter the cardholder name.", Alert.AlertType.WARNING);
            return false;
        }
        
        if (cardNumber.isEmpty()) {
            showAlert("Validation Error", "Please enter a valid card number.", Alert.AlertType.WARNING);
            return false;
        }
        
        String cleanedCardNumber = cardNumber.replaceAll("\\D", "");
        if (cleanedCardNumber.length() < 13 || cleanedCardNumber.length() > 19) {
            showAlert("Validation Error", "Card number must be between 13 and 19 digits.", Alert.AlertType.WARNING);
            return false;
        }
        
        if (expiryDate.isEmpty()) {
            showAlert("Validation Error", "Please enter the expiry date (MM/YY).", Alert.AlertType.WARNING);
            return false;
        }
        
        if (cvv.isEmpty() || cvv.length() < 3) {
            showAlert("Validation Error", "Please enter a valid CVV (3-4 digits).", Alert.AlertType.WARNING);
            return false;
        }
        
        return true;
    }

    private void clearPaymentForm() {
        cardholderNameField.clear();
        cardNumberField.clear();
        expiryDateField.clear();
        cvvField.clear();
    }

    private void clearWishlist() {
        try {
            for (WishlistDisplayItem item : new ArrayList<>(wishlistItems)) {
                wishlistService.removeItem(item.getId(), currentUser.getId());
            }
        } catch (Exception e) {
            System.err.println("Error clearing wishlist: " + e.getMessage());
        }
    }

    private void updateTotalPrice() {
        if (filteredItems == null) return;
        double total = filteredItems.stream().mapToDouble(WishlistDisplayItem::getPrice).sum();
        totalPriceLabel.setText("Total: " + formatPrice(total));
    }

    private void updateOrderSummary() {
        if (wishlistItems == null) {
            orderItemsLabel.setText("0 items");
            orderTotalLabel.setText("0.00 TND");
            return;
        }
        int count = wishlistItems.size();
        double total = wishlistItems.stream().mapToDouble(WishlistDisplayItem::getPrice).sum();
        orderItemsLabel.setText(count + " item" + (count != 1 ? "s" : ""));
        orderTotalLabel.setText(formatPrice(total));
    }

    @FXML
    private void handleRefresh() {
        loadWishlist();
        showAlert("Refreshed", "Wishlist reloaded", Alert.AlertType.INFORMATION);
    }

    @FXML
    private void handleDownloadWishlistPdf() {
        if (wishlistItems == null || wishlistItems.isEmpty()) {
            showAlert("Empty Wishlist", "Your wishlist is empty. Add items before downloading.", Alert.AlertType.WARNING);
            return;
        }
        
        try {
            double totalPrice = wishlistItems.stream().mapToDouble(WishlistDisplayItem::getPrice).sum();
            pdfService.exportWishlistPdf(new ArrayList<>(wishlistItems), totalPrice);
            showAlert("Success", "Complete wishlist PDF downloaded to your Downloads folder", Alert.AlertType.INFORMATION);
        } catch (Exception e) {
            showAlert("Error", "Could not download wishlist PDF: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private String formatPrice(double price) {
        return String.format(Locale.US, "%.2f TND", price);
    }

    private void showCelebrationGif() {
        try {
            String gifUrl = giphyService.getRandomCelebrationGifUrl();
            ImageView imageView = new ImageView(new Image(gifUrl, 420, 260, true, true, true));
            imageView.setFitWidth(420);
            imageView.setFitHeight(260);
            imageView.setPreserveRatio(true);

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Payment celebration");
            dialog.setHeaderText("Payment successful!");
            dialog.getDialogPane().setContent(imageView);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
            dialog.showAndWait();
        } catch (Exception e) {
            System.err.println("Could not load celebration GIF: " + e.getMessage());
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
