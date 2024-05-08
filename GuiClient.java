import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.*;

import javafx.scene.paint.Color;
import java.sql.Connection;
import javafx.scene.layout.StackPane;
import java.sql.Date;



public class GuiClient extends Application {

    Connection connection = null;

    Database base = new Database();
    private String clientEmail;
    private Map<String, Integer> freshAddresses;





    //---------------------------------CLIENT CODE START BELOW------------------------------


    public EventHandler<ActionEvent> clientUpdatestheirCreditCards(Stage primaryStage, String emailOfClient) {
        return event -> {
            Map<String, Integer> collectCards = base.getClientCreditCards(emailOfClient);
            Map<String, Integer> collectAddresses = base.getClientAddresses(emailOfClient);

            Button goBack = new Button("Go Back");
            logoutStyle(goBack);

            Button update = new Button("Update");
            saveStyleButton(update);

            ComboBox<String> creditCardComboBox = new ComboBox<>();
            styleComboBox(creditCardComboBox, "Pick a card to update");
            creditCardComboBox.getItems().addAll(collectCards.keySet());
            creditCardComboBox.setOnAction(e -> {
                String selectedCard = creditCardComboBox.getSelectionModel().getSelectedItem();
                Integer cardValue = collectCards.get(selectedCard);
                System.out.println("Selected Credit Card: " + selectedCard + ", Value: " + cardValue);
            });

            ComboBox<String> addressComboBox = new ComboBox<>();
            styleComboBox(addressComboBox, "Pick billing address");
            addressComboBox.getItems().addAll(collectAddresses.keySet());
            addressComboBox.setOnAction(e -> {
                String selectedAddress = addressComboBox.getSelectionModel().getSelectedItem();
                Integer addressValue = collectAddresses.get(selectedAddress);
                System.out.println("Selected Address: " + selectedAddress + ", Value: " + addressValue);
            });

            TextField newCreditCardTextField = new TextField();
            styleTextField(newCreditCardTextField, "Enter new credit card");
            newCreditCardTextField.setText(creditCardComboBox.getSelectionModel().getSelectedItem());

            TextField newAddressTextField = new TextField();
            styleTextField(newAddressTextField, "Enter new address");
            newAddressTextField.setText(addressComboBox.getSelectionModel().getSelectedItem());


            update.setOnAction(e -> {

                System.out.println(collectCards.get(creditCardComboBox.getSelectionModel().getSelectedItem())); // id  of credit card
                System.out.println(newCreditCardTextField.getText()); // new card details
                System.out.println(collectAddresses.get(addressComboBox.getSelectionModel().getSelectedItem())); // id of address
                System.out.println(newAddressTextField.getText());


                Integer addressId = collectAddresses.get(addressComboBox.getSelectionModel().getSelectedItem());
                String newAddress = newAddressTextField.getText();

                String oldCard = creditCardComboBox.getSelectionModel().getSelectedItem();
                String newCard = newCreditCardTextField.getText();


                base.updateAddress(addressId, newAddress);
                base.updateClientsCard(oldCard, newCard, addressId, emailOfClient);
                clientMainMenu(primaryStage, emailOfClient).handle(new ActionEvent());
            });

            goBack.setOnAction(clientMainMenu(primaryStage, emailOfClient));


            VBox vbox = new VBox(10, creditCardComboBox, newCreditCardTextField, addressComboBox, newAddressTextField, update, goBack);
            vbox.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
            vbox.setAlignment(Pos.CENTER);
            Scene scene = new Scene(vbox, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.show();
        };
    }


    public EventHandler<ActionEvent> chargeClient(Stage primaryStage, String emailOfClient, Integer documentId, Integer borrowId) {
        return event -> {
            BigDecimal moneyDue = base.moneyDue(connection, borrowId); // Assuming 'base' and 'connection' are accessible here
            Map<String, Integer> clientCards = base.getClientCreditCards(emailOfClient); // Assuming this returns a map of card names to some identifiers

            TextField balanceDueField = new TextField();
            styleTextField(balanceDueField, "Your Balance due is: " + moneyDue);
            System.out.println("Money Due: " + moneyDue);
            balanceDueField.setEditable(false);

            ComboBox<String> cardComboBox = new ComboBox<>();
            styleComboBox(cardComboBox, "Pick a Card to Pay");
            clientCards.forEach((cardName, cardId) -> cardComboBox.getItems().add(cardName));


            Button payButton = new Button("Confirm and Pay");
            styleButton(payButton);
            payButton.setDisable(true);


            cardComboBox.setOnAction(e -> payButton.setDisable(cardComboBox.getSelectionModel().isEmpty()));

            payButton.setOnAction(e -> {


                // Here we delete the record
                Integer lendedCopies = base.deleteBorrowTableRecord(connection, emailOfClient, documentId);

                // Now we update the copies
                base.returnAndUpdateCopies(connection, documentId, lendedCopies);

                clientMainMenu(primaryStage, emailOfClient).handle(new ActionEvent());


            });

            // Layout setup
            VBox layout = new VBox(10); // 10 is the spacing between elements
            layout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
            layout.setAlignment(Pos.CENTER);
            layout.getChildren().addAll(balanceDueField, cardComboBox, payButton);

            // Scene setup
            Scene scene = new Scene(layout, 800, 800); // Adjust size as needed
            primaryStage.setScene(scene);
            primaryStage.setTitle("Charge Client");
            primaryStage.show();
        };
    }


    public EventHandler<ActionEvent> confirmReturn(Stage primaryStage, String emailOfClient, Integer documentId) {
        return event -> {
            DatePicker datePicker = new DatePicker();
            VBox vbox = new VBox(datePicker);
            vbox.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
            vbox.setAlignment(Pos.CENTER);
            Scene scene = new Scene(vbox, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.show();

            datePicker.setOnAction(e -> {
                LocalDate localDate = datePicker.getValue();
                if (localDate != null) {
                    System.out.println("Selected date: " + localDate);

                    Date date = Date.valueOf(localDate);

                    try {
                        Integer lateWeeks = base.calculateWeeksLate(connection, documentId, date);
                        System.out.println("Total Late weeks: " + (lateWeeks != null ? lateWeeks : "Error or no data found"));

                        Integer borrowId = base.getBorrowId(connection, documentId, emailOfClient);
                        System.out.println("Borrow ID: " + borrowId);

                        base.updateTransactionTable(connection, documentId, lateWeeks, borrowId); // need other id
                        chargeClient(primaryStage, emailOfClient, documentId, borrowId).handle(new ActionEvent());


                    } catch (Exception ex) {
                        System.out.println("Failed to calculate late weeks: " + ex.getMessage());
                    }
                } else {
                    System.out.println("No date selected.");
                }
            });
        };
    }




    public EventHandler<ActionEvent> returningDocuments(Stage primaryStage, String emailOfClient) {
        return event -> {
            Map<String, Integer> borrowedDocuments = base.displayAllBorrowedDocuments(connection, emailOfClient);
            ObservableList<String> documentTitles = FXCollections.observableArrayList(borrowedDocuments.keySet());

            ComboBox<String> documentComboBox = new ComboBox<>(documentTitles);
            styleComboBox(documentComboBox, "Select a document to return");

            // Set up a listener for the ComboBox selections
            documentComboBox.setOnAction(e -> {
                String selectedTitle = documentComboBox.getSelectionModel().getSelectedItem();
                if (selectedTitle != null) {
                    Integer documentId = borrowedDocuments.get(selectedTitle);

                    confirmReturn(primaryStage, emailOfClient, documentId).handle(new ActionEvent());

                }
            });

            Button goBack = new Button("Go Back");
            logoutStyle(goBack);
            goBack.setOnAction(clientMainMenu(primaryStage, emailOfClient));


            VBox layout = new VBox(10);
            layout.getChildren().addAll(documentComboBox, goBack);
            layout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
            layout.setAlignment(Pos.CENTER);

            Scene scene = new Scene(layout, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.show();
        };
    }


    private EventHandler<KeyEvent> numericValidation(final Integer maxLenght) {
        return e -> {
            TextField txt_TextField = (TextField) e.getSource();
            if (txt_TextField.getText().length() >= maxLenght) {  // limit textfield to 3 characters
                e.consume();
            }
            if(e.getCharacter().matches("[^0-9]")){  // if not a number
                e.consume();
            }
        };
    }

    public EventHandler<ActionEvent> documentBorrowed(Stage primaryStage, String emailOfClient, Integer document_id) {
        return event -> {

            Integer remainingCopies = base.retrieveCopies(connection, document_id);

            if(remainingCopies >= 1){

                Label instructionLabel = new Label("How many copies would you like?");

                TextField numberField = new TextField();
                styleTextField(numberField, "Enter number here");
                numberField.addEventFilter(KeyEvent.KEY_TYPED, numericValidation(10));

                // Button to confirm checkout
                Button confirmButton = new Button("Confirm and Checkout Document");
                styleButton(confirmButton);


                confirmButton.setOnAction(e -> {
                    if(Integer.parseInt(numberField.getText()) <= remainingCopies) {
                        System.out.println("Copies wanted: " + Integer.parseInt(numberField.getText()));
                        base.insertCopiesLended(connection, document_id, Integer.parseInt(numberField.getText()));

                        base.updateBorrowTable(connection, document_id, emailOfClient, Integer.parseInt(numberField.getText()));

                        clientMainMenu(primaryStage, emailOfClient).handle(new ActionEvent());

                    }else{
                        System.out.println("Not enough copies available");
                    }
//                    handleCheckout(numberField.getText());
                });

                // Button to go back
                Button goBackButton = new Button("Go Back");
                logoutStyle(goBackButton);
                goBackButton.setOnAction(e -> primaryStage.close());  // Close the stage to simulate going back

                // Layout setup
                VBox layout = new VBox(10);
                layout.getChildren().addAll(instructionLabel, numberField, confirmButton, goBackButton);
                layout.setAlignment(Pos.CENTER);
                layout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

                // Scene and stage setup
                Scene scene = new Scene(layout, 800, 800);
                primaryStage.setTitle("Checkout Document");
                primaryStage.setScene(scene);
                primaryStage.show();

            }else{

                TextField messageField = new TextField();
                styleTextField(messageField, "This document cannot be borrowed as no copies are available! Please pick another document or look again later for this.");
                messageField.setEditable(false);


                Button goBackButton = new Button("Go Back");
                logoutStyle(goBackButton);
                goBackButton.setOnAction(clientSearch(primaryStage, emailOfClient));  // Assuming we just close this stage for simplicity

                // Layout Setup
                VBox layout = new VBox(10, messageField, goBackButton);
                layout.setAlignment(Pos.CENTER);  // Center the VBox contents
                layout.setSpacing(20);
                layout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");// Set spacing between elements

                // Scene and Stage Setup
                Scene scene = new Scene(layout, 800, 800);  // Set the scene size
                primaryStage.setTitle("Document Unavailable");
                primaryStage.setScene(scene);
                primaryStage.show();


            }

            System.out.println("total_copies: " + remainingCopies);
            System.out.println("Email: " + emailOfClient);
            System.out.println("Documet_id: " + document_id);



        };
    }


    public EventHandler<ActionEvent> clientSearch(Stage primaryStage, String emailOfClient) {
        return event -> {
            TextField searchField = new TextField();
            styleTextField(searchField, "Enter search term");
            searchField.setMaxWidth(300); // Style for text field

            Button searchButton = new Button("Search");
            styleButton(searchButton);

            VBox resultsBox = new VBox(5); // Vertical box with spacing of 5
            resultsBox.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            searchButton.setOnAction(null);
            searchButton.setOnAction(ev -> {
                String searchQuery = searchField.getText().toLowerCase();
                resultsBox.getChildren().clear(); // Clear previous results
                HashSet<String> searchWords = new HashSet<>(Arrays.asList(searchQuery.split("\\s+")));

                Map<String, Integer> booksFound = base.clientSearchBooks(connection, searchQuery);
                Map<String, Integer> magazinesFound = base.clientSearchMagazines(connection, searchQuery);
                Map<String, Integer> journalsFound = base.clientSearchJournal(connection, searchQuery);
                Map<String, Integer> combination = base.combineAndSortResults(booksFound, magazinesFound, journalsFound, searchQuery);

                combination.forEach((key, value) -> {
                    Button resultButton = new Button();
                    styleButton(resultButton);

                    TextFlow textFlow = new TextFlow();
                    textFlow.getChildren().clear();
                    textFlow.setTextAlignment(TextAlignment.CENTER);

                    // Splitting the key into lines
                    String[] lines = key.split("\n");

                    // Adding each line with correct formatting
                    for (String line : lines) {
                        // Adding each word with correct formatting
                        String[] words = line.split("\\s+");
                        for (String word : words) {
                            Text textWord = new Text(word + " ");

                            if (searchWords.contains(word.toLowerCase())) {
                                textWord.setFill(Color.YELLOW); // Highlight matching words
                                textWord.setStyle("-fx-font-weight: bold;");
                            }
                            textFlow.getChildren().add(textWord);
                        }

                        // Adding a line break after each line
                        Text textLineBreak = new Text("\n");
                        textFlow.getChildren().add(textLineBreak);
                    }

                    resultButton.setGraphic(textFlow);

                    resultButton.setMaxWidth(500);
                    resultButton.setMaxHeight(150);

                    resultButton.setOnAction(e -> {
                        System.out.println("ID of selected item: " + value);
                        documentBorrowed(primaryStage, emailOfClient, value).handle(new ActionEvent());
                    });

                    resultsBox.setAlignment(Pos.CENTER);
                    resultsBox.getChildren().add(resultButton);
                });

            });

            Button goBack = new Button("Go Back");
            goBack.setOnAction(clientMainMenu(primaryStage, emailOfClient));
            logoutStyle(goBack);

            HBox searchBox = new HBox(10, searchField, searchButton);
            searchBox.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
            searchBox.setAlignment(Pos.CENTER);

            VBox mainLayout = new VBox(10, searchBox, resultsBox, goBack);
            mainLayout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
            mainLayout.setAlignment(Pos.CENTER);

            Scene scene = new Scene(mainLayout, 800, 800);
            primaryStage.setTitle("Search Interface");
            primaryStage.setScene(scene);
            primaryStage.show();
        };
    }




//    public EventHandler<ActionEvent> clientSearch(Stage primaryStage, String emailOfClient) {
//        return event -> {
//            TextField searchField = new TextField();
//            styleTextField(searchField, "Enter search term");
//            searchField.setMaxWidth(300); // Style for text field
//
//            Button searchButton = new Button("Search");
//            styleButton(searchButton);
//
//            VBox resultsBox = new VBox(5); // Vertical box with spacing of 5
//            resultsBox.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
//
//            searchButton.setOnAction(null);
//            searchButton.setOnAction(ev -> {
//                String searchQuery = searchField.getText().toLowerCase();
//                resultsBox.getChildren().clear(); // Clear previous results
//                HashSet<String> searchWords = new HashSet<>(Arrays.asList(searchQuery.split("\\s+")));
//
//                Map<String, Integer> booksFound = base.clientSearchBooks(connection, searchQuery);
//                Map<String, Integer> magazinesFound = base.clientSearchMagazines(connection, searchQuery);
//                Map<String, Integer> journalsFound = base.clientSearchJournal(connection, searchQuery);
//                Map<String, Integer> combination = base.combineAndSortResults(booksFound, magazinesFound, journalsFound, searchQuery);
//
//                combination.forEach((key, value) -> {
//
//
//                    Button resultButton = new Button();
//                    styleButton(resultButton);
//
//                    TextFlow textFlow = new TextFlow();
//                    textFlow.getChildren().clear();
//                    textFlow.setTextAlignment(TextAlignment.CENTER);
//
//                    // Adding each word with correct formatting
//                    Arrays.stream(key.split("\n")).forEach(line -> {
//
////                        System.out.println("Line: "+line);
//
//                        Text textLine = new Text(line + "\n");
//                        Arrays.stream(line.split("\\s+")).forEach(word -> {
//                            Text textWord = new Text(word + " ");
//
////                            System.out.println("word: " + word);
//
//                            if (searchWords.contains(word.toLowerCase())) {
//                                textWord.setFill(Color.YELLOW); // Highlight matching words
//                                textWord.setStyle("-fx-font-weight: bold;");
//                            }
//                            textFlow.getChildren().add(textWord);
//                        });
//
//                        textFlow.getChildren().add(textLine);
//
//                    });
//
//
//                    resultButton.setGraphic(textFlow);
//                    System.out.println("Text:" + resultButton.getText());
////                    textFlow.getChildren().clear(); // does not help the cause
//
//                    resultButton.setMaxWidth(500);
//                    resultButton.setMaxHeight(150);
//
//
//
//                    resultButton.setOnAction(e -> {
//                        System.out.println("ID of selected item: " + value);
//                        documentBorrowed(primaryStage, emailOfClient, value).handle(new ActionEvent());
//                    });
//
//
//                    resultsBox.setAlignment(Pos.CENTER);
//                    resultsBox.getChildren().add(resultButton);
//                });
//            });
//
//            Button goBack = new Button("Go Back");
//            goBack.setOnAction(clientMainMenu(primaryStage, emailOfClient));
//            logoutStyle(goBack);
//
//            HBox searchBox = new HBox(10, searchField, searchButton);
//            searchBox.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
//            searchBox.setAlignment(Pos.CENTER);
//
//            VBox mainLayout = new VBox(10, searchBox, resultsBox, goBack);
//            mainLayout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
//            mainLayout.setAlignment(Pos.CENTER);
//
//            Scene scene = new Scene(mainLayout, 800, 800);
//            primaryStage.setTitle("Search Interface");
//            primaryStage.setScene(scene);
//            primaryStage.show();
//        };
//    }





    EventHandler<ActionEvent> clientMainMenu(Stage primaryStage, String emailOfClient) {
        return event -> {

            VBox vbox = new VBox(10);  // Vertical box layout with spacing of 10
            vbox.setAlignment(Pos.CENTER);  // Center the VBox contents
            vbox.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            // Create buttons
            Button borrowButton = new Button("Borrow a Document");
            styleButton(borrowButton);

            Button returnButton = new Button("Return a Document");
            styleButton(returnButton);

            Button updatePaymentButton = new Button("Update Payment Information");
            styleButton(updatePaymentButton);

            Button logoutButton = new Button("Logout");
            logoutStyle(logoutButton);

            // Add buttons to the VBox
            vbox.getChildren().addAll(borrowButton, returnButton, updatePaymentButton, logoutButton);

            // Set action events for each button
            borrowButton.setOnAction(e -> {
                System.out.println("Borrow a Document clicked");

                clientSearch(primaryStage, emailOfClient).handle(new ActionEvent());


            });

            returnButton.setOnAction(e -> {
                System.out.println("Return a Document clicked");
                returningDocuments(primaryStage, emailOfClient).handle(new ActionEvent());
            });

            updatePaymentButton.setOnAction(clientUpdatestheirCreditCards(primaryStage, emailOfClient));

            logoutButton.setOnAction(e -> {
                mainMenu(primaryStage).handle(new ActionEvent());
            });

            Scene scene = new Scene(vbox, 800, 800);
            primaryStage.setTitle("Client Main Menu");
            primaryStage.setScene(scene);



        };
    }



    EventHandler<ActionEvent> clientAuthentication(Stage primaryStage) {
        return event -> {
            VBox root = new VBox(20);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setAlignment(Pos.CENTER);

            TextField emailField = new TextField();
            styleTextField(emailField, "Enter your email");

            PasswordField passwordField = new PasswordField();
            stylePasswordField(passwordField, "Enter your password");

            Button loginButton = new Button("Login");
            styleButton(loginButton);

            loginButton.setOnAction(e -> {
                if(base.verifyClientLoginAttempt(connection, emailField.getText(), passwordField.getText())){
                    clientMainMenu(primaryStage, emailField.getText()).handle(new ActionEvent());
                    System.out.println("Login Successful");
                }
            });

            Button goBackButton = new Button("Go Back");
            logoutStyle(goBackButton);
            goBackButton.setOnAction(e -> {
                mainMenu(primaryStage).handle(new ActionEvent());
            });

            grid.add(new Label("Email:"), 0, 0);
            grid.add(emailField, 1, 0);
            grid.add(new Label("Password:"), 0, 1);
            grid.add(passwordField, 1, 1);

            root.getChildren().addAll(grid, loginButton, goBackButton);

            Scene scene = new Scene(root, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.show();
        };
    }



    //-----------------------------------CLIENT CODE ABOVE------------------------------


    EventHandler<ActionEvent> updateSpecificArticle(Stage primaryStage, Integer articleId, Integer documentId) {
        return actionEvent -> {

            Object[] articleDetails = base.returnArticleDetails(connection, articleId);

            if (articleDetails == null) {
                System.out.println("Article not found.");
                return;
            }

            VBox layout = new VBox(10);
            layout.setPadding(new Insets(20));
            layout.setAlignment(Pos.CENTER);

            GridPane grid = new GridPane();
            grid.setVgap(10);
            grid.setHgap(10);
            grid.setAlignment(Pos.CENTER);

            String[] labels = {"Issue Number", "Volume Number", "Year", "Page Numbers"};
            TextField[] textFields = new TextField[labels.length];


            for (int i = 0; i < labels.length; i++) {
                Label label = new Label(labels[i] + ":");

                TextField textField = new TextField();
                styleTextField(textField, "");

                if (articleDetails[i] != null) {
                    textField.setText(articleDetails[i].toString());
                }
                textFields[i] = textField;
                grid.add(label, 0, i);
                grid.add(textField, 1, i);
            }

            Button saveButton = new Button("Save");
            saveStyleButton(saveButton);

            saveButton.setOnAction(e -> {

                Object[] updatedArticleDetails = new Object[4];
                updatedArticleDetails[0] = Integer.parseInt(textFields[0].getText());
                updatedArticleDetails[1] = Integer.parseInt(textFields[1].getText());
                updatedArticleDetails[2] = Integer.parseInt(textFields[2].getText());
                updatedArticleDetails[3] = textFields[3].getText();


                base.updateArticleDetails(connection, articleId, updatedArticleDetails);
                pickDocumentToAdd(primaryStage).handle(new ActionEvent());

                System.out.println("Article details updated successfully.");
            });

            Button deleteButton = new Button("Delete");
            styleButton(deleteButton);

            deleteButton.setOnAction(e -> {

                base.deleteAnArticle(connection, articleId);
                pickDocumentToAdd(primaryStage).handle(new ActionEvent());
                System.out.println("Deleted");

            });

            Button goBackButton = new Button("Go Back");
            logoutStyle(goBackButton);
            goBackButton.setOnAction(updateArticles(primaryStage, documentId));

            layout.getChildren().addAll(grid, saveButton, deleteButton, goBackButton);
            layout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Scene scene = new Scene(layout, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Edit Article Details");
            primaryStage.show();

        };
    }



    EventHandler<ActionEvent> updateArticles(Stage primaryStage, Integer documentId) {
        return actionEvent -> {


            Map<String, Integer> articlesMap = base.getArticles(connection, documentId);

            VBox layout = new VBox(10);
            layout.setPadding(new Insets(20));
            layout.setAlignment(Pos.CENTER);

            Label label = new Label("Choose an article to edit:");
            ComboBox<String> comboBox = new ComboBox<>();
            styleComboBox(comboBox, "Choose an Article to Edit");

            articlesMap.forEach((title, id) -> comboBox.getItems().add(title));

            comboBox.setOnAction(e -> {
                String selectedTitle = comboBox.getValue();
                Integer selectedId = articlesMap.get(selectedTitle);

                updateSpecificArticle(primaryStage, selectedId, documentId).handle(new ActionEvent());
                System.out.println("Selected Article: " + selectedTitle + " (ID: " + selectedId + ")");
            });

            Button goBackButton = new Button("Go Back");
            logoutStyle(goBackButton);
            goBackButton.setOnAction(updateSpecificJournal(primaryStage, documentId));

            layout.getChildren().addAll(label, comboBox, goBackButton);
            layout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Scene scene = new Scene(layout, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Update Articles");
            primaryStage.show();


        };
    }



    //-------------------------Updating Journal Starts-------------------------


    EventHandler<ActionEvent> updateSpecificJournal(Stage primaryStage, Integer documentId){
        return actionEvent -> {

            Object[] journalDetails = base.returnJournalDetails(connection, documentId);

            if (journalDetails == null) {
                System.out.println("Journal not found.");
                return;
            }

            VBox layout = new VBox(10);
            layout.setPadding(new Insets(20));
            layout.setAlignment(Pos.CENTER);

            GridPane grid = new GridPane();
            grid.setVgap(10);
            grid.setHgap(10);
            grid.setAlignment(Pos.CENTER);


            String[] labels = {"Journal Name", "Publisher", "Total Copies"};
            TextField[] textFields = new TextField[labels.length];

            for (int i = 0; i < labels.length; i++) {
                Label label = new Label(labels[i] + ":");

                TextField textField = new TextField();
                styleTextField(textField, "");

                if (journalDetails[i] != null) {
                    textField.setText(journalDetails[i].toString());
                }
                textFields[i] = textField;
                grid.add(label, 0, i);
                grid.add(textField, 1, i);
            }

            Button saveButton = new Button("Save & Continue to Edit Articles");
            saveStyleButton(saveButton);

            saveButton.setOnAction(e -> {


                Object[] updatedJournalDetails = new Object[labels.length];  // Only include editable fields
                updatedJournalDetails[0] = textFields[0].getText();  // Journal Name, remains a String
                updatedJournalDetails[1] = textFields[1].getText();  // Publisher, remains a String
                updatedJournalDetails[2] = Integer.parseInt(textFields[2].getText());  // Total Copies, convert to Integer


                base.updateJournalDetails(connection, documentId, updatedJournalDetails);
                updateArticles(primaryStage, documentId).handle(new ActionEvent());

            });

            Button deleteButton = new Button("Delete Whole Journal");
            styleButton(deleteButton);
            deleteButton.setOnAction(e -> {

                if (Integer.parseInt(journalDetails[2].toString()) - Integer.parseInt(journalDetails[3].toString()) == Integer.parseInt(journalDetails[2].toString())) {

                    base.deleteADocument(connection, documentId);
                    pickDocumentToAdd(primaryStage).handle(new ActionEvent());

                    System.out.println("Deleted");


                } else {
                    System.out.println("Cannot delete, copies are still lended out.");
                }
            });

            Button goBackButton = new Button("Go Back");
            logoutStyle(goBackButton);
            goBackButton.setOnAction(updateJournals(primaryStage));

            layout.getChildren().addAll(grid, saveButton, deleteButton, goBackButton);
            layout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Scene scene = new Scene(layout, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Edit Journal Details");
            primaryStage.show();

        };
    }

    EventHandler<ActionEvent> updateJournals(Stage primaryStage){
        return actionEvent -> {


            Map<String, Integer> journalsMap = base.getJournals(connection);


            VBox vBox = new VBox(10);
            vBox.setAlignment(Pos.CENTER);
            vBox.setPadding(new Insets(20));

            ComboBox<String> comboBox = new ComboBox<>();
            styleComboBox(comboBox, "Pick a Journal to update");

            journalsMap.forEach((title, id) -> comboBox.getItems().add(title));

            comboBox.setOnAction(e -> {

                String selectedTitle = comboBox.getSelectionModel().getSelectedItem();

                if (selectedTitle != null) {

                    Integer documentId = journalsMap.get(selectedTitle);
                    System.out.println("Selected Journal's Document ID: " + documentId);

                    updateSpecificJournal(primaryStage, documentId).handle(new ActionEvent());

                }
            });

            Button goBack = new Button("Go Back");
            logoutStyle(goBack);
            goBack.setOnAction(pickDocumentToAdd(primaryStage));

            vBox.getChildren().addAll(comboBox, goBack);
            vBox.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Scene scene = new Scene(vBox, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Select a Journal");
            primaryStage.show();





            // Note to myself:
            // WORK ON THIS::
            // NOW FIND WHAT WAS CLICKED. STORE THAT DOCUMENT ID
            // CALL DATABASE TO OPEN ARTICLES TABLE
            // GET ALL ARTICLES. IN A MAP STORE THE MAP<ARTICLE NAME , ARTICLE ID>
            // THIS WAY WE CAN GET ALL THE ARTICLES ASSOCIATED WITH THE ARTICLE ID WITH SAME JOURNAL ID
            // AND OPEN USING THE ARTICLE ID TO EDIT THE ARTICLES. EASY PEEZY


        };

    }

    //-------------------------Updating journals End-------------------------


    //-------------------------Updating Magazines Start-------------------------

    EventHandler<ActionEvent> updateSpecificMagainze(Stage primaryStage, Integer documentId){
        return actionEvent -> {

            Object[] magazineDetails = base.returnMagazineDetails(connection, documentId);

            if (magazineDetails == null) {
                System.out.println("Magazine not found.");
                return;
            }

            VBox layout = new VBox(10);
            layout.setPadding(new Insets(20));
            layout.setAlignment(Pos.CENTER);

            GridPane grid = new GridPane();
            grid.setVgap(10);
            grid.setHgap(10);
            grid.setAlignment(Pos.CENTER);

            String[] labels = {"Magazine Name", "ISBN", "Publisher", "Year", "Month", "Total Copies"};

            TextField[] textFields = new TextField[labels.length];
            for (int i = 0; i < labels.length; i++) {
                Label label = new Label(labels[i] + ":");
                TextField textField = new TextField();
                styleTextField(textField, "");
                if (magazineDetails[i] != null) {
                    textField.setText(magazineDetails[i].toString());
                }
                textFields[i] = textField;
                grid.add(label, 0, i);
                grid.add(textField, 1, i);
            }

            Button saveButton = new Button("Save");
            saveStyleButton(saveButton);
            saveButton.setOnAction(e -> {

//                Object[] updatedMagazineDetails = new Object[labels.length];

//                for (int i = 0; i < updatedMagazineDetails.length; i++) {
//                    updatedMagazineDetails[i] = textFields[i].getText();
//                }



                Object[] updatedMagazineDetails = new Object[6];
                updatedMagazineDetails[0] = textFields[0].getText();
                updatedMagazineDetails[1] = textFields[1].getText();
                updatedMagazineDetails[2] = textFields[2].getText();
                updatedMagazineDetails[3] = Integer.parseInt(textFields[3].getText());
                updatedMagazineDetails[4] = Integer.parseInt(textFields[4].getText());
                updatedMagazineDetails[5] = Integer.parseInt(textFields[5].getText());

                base.updateMagazineDetails(connection, documentId, updatedMagazineDetails);


                pickDocumentToAdd(primaryStage).handle(new ActionEvent());

            });

            Button deleteButton = new Button("Delete");
            styleButton(deleteButton);
            deleteButton.setOnAction(e -> {
                if ((Integer) magazineDetails[5] - (Integer) magazineDetails[6] == (Integer) magazineDetails[5]) {

                    base.deleteADocument(connection, documentId);
                    pickDocumentToAdd(primaryStage).handle(new ActionEvent());

                    System.out.println("Deleted");
                } else {
                    System.out.println("Cannot delete, copies are still lended out.");
                }
            });

            Button goBackButton = new Button("Go Back");
            logoutStyle(goBackButton);
            goBackButton.setOnAction(updateMagazine(primaryStage));

            layout.getChildren().addAll(grid, saveButton, deleteButton, goBackButton);
            layout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Scene scene = new Scene(layout, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Edit Magazine Details");
            primaryStage.show();

        };
    }


    EventHandler<ActionEvent> updateMagazine(Stage primaryStage){
        return actionEvent -> {


            Map<String, Integer> MagazinesMap = base.getMagazines(connection);

//            for (Map.Entry<String, Integer> entry : MagazinesMap.entrySet()) {
//                System.out.println(entry.getKey() + ": " + entry.getValue());
//            }

            VBox vBox = new VBox(10);
            vBox.setAlignment(Pos.CENTER);
            vBox.setPadding(new Insets(20));

            ComboBox<String> comboBox = new ComboBox<>();
            styleComboBox(comboBox, "Pick a Magazine to update");

            MagazinesMap.forEach((title, id) -> comboBox.getItems().add(title));

            comboBox.setOnAction(e -> {

                String selectedTitle = comboBox.getSelectionModel().getSelectedItem();

                if (selectedTitle != null) {

                    Integer documentId = MagazinesMap.get(selectedTitle);

                    updateSpecificMagainze(primaryStage, documentId).handle(new ActionEvent());

//                    System.out.println("Selected Magazine's Document ID: " + documentId);

                }
            });

            Button goBack = new Button("Go Back");
            logoutStyle(goBack);
            goBack.setOnAction(pickDocumentToAdd(primaryStage));

            vBox.getChildren().addAll(comboBox, goBack);
            vBox.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Scene scene = new Scene(vBox, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Select a Magazine");
            primaryStage.show();


        };

    }

    //-------------------------Updating Magazines End-------------------------


    //-------------------------Updating Books Start-------------------------


    public EventHandler<ActionEvent> updateSpecificBook(Stage primaryStage, Integer documentId) {
        return actionEvent -> {

            Object[] bookDetails = base.returnBookDetails(connection, documentId);

            if (bookDetails == null) {
                System.out.println("Book not found.");
                return;
            }

            VBox layout = new VBox(10);
            layout.setPadding(new Insets(20));
            layout.setAlignment(Pos.CENTER);

            GridPane grid = new GridPane();
            grid.setVgap(10);
            grid.setHgap(10);
            grid.setAlignment(Pos.CENTER);

            String[] labels = {"Title", "Authors", "ISBN", "Publisher", "Edition", "Year", "Num Pages", "Total Copies"};

            TextField[] textFields = new TextField[labels.length];
            for (int i = 0; i < labels.length; i++) {
                Label label = new Label(labels[i] + ":");
                TextField textField = new TextField();
                styleTextField(textField, "");
                if (bookDetails[i] != null) {
                    textField.setText(bookDetails[i].toString());
                }
                textFields[i] = textField;
                grid.add(label, 0, i);
                grid.add(textField, 1, i);
            }

            TextField isbnTextField = textFields[2];
            isbnTextField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                    if (newValue.length() == 13) {
                        System.out.println("ISBN is exactly 13 digits.");
                    } else if (newValue.length() > 13) {
                        System.out.println("ISBN cannot be more than 13 digits.");
                        isbnTextField.setText(oldValue); // Optionally prevent further input
                    }
                }
            });

            Button saveButton = new Button("Save");
            saveStyleButton(saveButton);

            saveButton.setOnAction(e -> {
                if (isbnTextField.getText().length() != 13) {
                    System.out.println("ISBN must be exactly 13 digits before saving.");
                    return;
                }


                Object[] updatedBookDetails = new Object[8];
                for (int i = 0; i < updatedBookDetails.length; i++) {
                    if (i == 4 || i == 5 || i == 6 || i == 7) {
                        try {
                            updatedBookDetails[i] = Integer.parseInt(textFields[i].getText());
                        } catch (NumberFormatException exception) {
                            System.out.println("Error: Invalid input for " + labels[i] + ". Please enter a valid integer.");
                            return;
                        }
                    } else {
                        updatedBookDetails[i] = textFields[i].getText();
                    }
                }

                base.updateBookDetails(connection, documentId, updatedBookDetails);
                pickDocumentToAdd(primaryStage).handle(new ActionEvent());

            });

            Button deleteButton = new Button("Delete");
            styleButton(deleteButton);
            deleteButton.setOnAction(e -> {
                if (Integer.parseInt(bookDetails[7].toString()) - Integer.parseInt(bookDetails[8].toString()) == Integer.parseInt(bookDetails[7].toString())) {

                    base.deleteADocument(connection, documentId);
                    pickDocumentToAdd(primaryStage).handle(new ActionEvent());
                    System.out.println("Deleted");
                } else {
                    System.out.println("Cannot delete, copies are still lended out.");
                }
            });

            Button goBackButton = new Button("Go Back");
            logoutStyle(goBackButton);
            goBackButton.setOnAction(updateBooks(primaryStage));

            layout.getChildren().addAll(grid, saveButton, deleteButton, goBackButton);
            layout.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Scene scene = new Scene(layout, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Edit Book Details");
            primaryStage.show();
        };
    }

    EventHandler<ActionEvent> updateBooks(Stage primaryStage){
        return actionEvent -> {


            Map<String, Integer> booksMap = base.getBooks(connection);

//            for (Map.Entry<String, Integer> entry : booksMap.entrySet()) {
//                System.out.println(entry.getKey() + ": " + entry.getValue());
//            }

            VBox vBox = new VBox(10);
            vBox.setAlignment(Pos.CENTER);
            vBox.setPadding(new Insets(20));

            ComboBox<String> comboBox = new ComboBox<>();
            styleComboBox(comboBox, "Pick a book to update");

            booksMap.forEach((title, id) -> comboBox.getItems().add(title));

            comboBox.setOnAction(e -> {

                String selectedTitle = comboBox.getSelectionModel().getSelectedItem();

                if (selectedTitle != null) {

                    Integer documentId = booksMap.get(selectedTitle);
                    System.out.println("Selected Book's Document ID: " + documentId);
                    updateSpecificBook(primaryStage, documentId).handle(new ActionEvent());

                }
            });

            Button goBack = new Button("Go Back");
            logoutStyle(goBack);
            goBack.setOnAction(pickDocumentToAdd(primaryStage));

            vBox.getChildren().addAll(comboBox, goBack);
            vBox.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Scene scene = new Scene(vBox, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Select a Book");
            primaryStage.show();

        };

    }


    //-------------------------Updating Books End-------------------------


    EventHandler<ActionEvent> addBook(Stage primaryStage){
        return actionEvent -> {

            Integer documentId = base.enterDocumentAndGetDocumentId("Book");
            System.out.println("Here inside book");


            Button goBack = new Button("Go Back");
            logoutStyle(goBack);
            goBack.setOnAction(e -> {
                base.deleteADocument(connection, documentId);
                pickDocumentToAdd(primaryStage).handle(new ActionEvent());
            });

            GridPane grid = new GridPane();
            grid.setAlignment(Pos.CENTER);
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            // TextFields for book information
            TextField titleField = new TextField();
            TextField authorsField = new TextField();
            TextField isbnField = new TextField();
            TextField publisherField = new TextField();
            TextField editionField = new TextField();
            TextField yearField = new TextField();
            TextField numPagesField = new TextField();
            TextField totalCopiesField = new TextField();

            styleTextField(titleField, "");
            styleTextField(authorsField, "");
            styleTextField(isbnField, "");
            styleTextField(publisherField, "");
            styleTextField(editionField, "");
            styleTextField(yearField, "");
            styleTextField(numPagesField, "");
            styleTextField(totalCopiesField, "");

            // Validation Text
            Text validationText = new Text();
            isbnField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.matches("\\d*")) {  // Allow only digits
                    isbnField.setText(newValue.replaceAll("[^\\d]", ""));
                }
                if (newValue.length() != 13) {
                    validationText.setFill(Color.RED);
                    validationText.setText("ISBN must be exactly 13 digits");
                } else {
                    validationText.setFill(Color.GREEN);
                    validationText.setText("ISBN format is correct");
                }
            });

            Button submitButton = new Button("Upload Book");
            styleButton(submitButton);

            submitButton.setOnAction(e -> {
                    System.out.println("Title: " + titleField.getText());
                    System.out.println("Authors: " + authorsField.getText());
                    System.out.println("ISBN: " + isbnField.getText());
                    System.out.println("Publisher: " + publisherField.getText());
                    System.out.println("Edition: " + editionField.getText());
                    System.out.println("Year: " + yearField.getText());
                    System.out.println("Number of Pages: " + numPagesField.getText());
                    System.out.println("Total Copies: " + totalCopiesField.getText());

                    base.uploadBook(documentId, titleField.getText(), authorsField.getText(),isbnField.getText(), publisherField.getText(),
                            editionField.getText(), yearField.getText(), numPagesField.getText(), totalCopiesField.getText());

                librarianMenu(primaryStage).handle(new ActionEvent());


            });

            // Layout the form
            grid.add(new Label("Title:"), 0, 0);
            grid.add(titleField, 1, 0);
            grid.add(new Label("Authors:"), 0, 1);
            grid.add(authorsField, 1, 1);
            grid.add(new Label("ISBN (13 digits):"), 0, 2);
            grid.add(isbnField, 1, 2);
            grid.add(validationText, 2, 2);
            grid.add(new Label("Publisher:"), 0, 3);
            grid.add(publisherField, 1, 3);
            grid.add(new Label("Edition:"), 0, 4);
            grid.add(editionField, 1, 4);
            grid.add(new Label("Year:"), 0, 5);
            grid.add(yearField, 1, 5);
            grid.add(new Label("Number of Pages:"), 0, 6);
            grid.add(numPagesField, 1, 6);
            grid.add(new Label("Total Copies:"), 0, 7);
            grid.add(totalCopiesField, 1, 7);
            grid.add(submitButton, 1, 8);
            grid.add(goBack, 1, 9);

            Scene scene = new Scene(grid, 800, 800);
            primaryStage.setTitle("Enter New Book Details");
            primaryStage.setScene(scene);
            primaryStage.show();

        };

    }


    EventHandler<ActionEvent> addMagazine(Stage primaryStage){
        return actionEvent -> {

            Integer documentId = base.enterDocumentAndGetDocumentId("Magazine");
            System.out.println("Here inside Magazine");

            Button goBack = new Button("Go Back");
            logoutStyle(goBack);
            goBack.setOnAction(e -> {
                base.deleteADocument(connection, documentId);
                pickDocumentToAdd(primaryStage).handle(new ActionEvent());
            });

            GridPane grid = new GridPane();
            grid.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
            grid.setAlignment(Pos.CENTER);
            grid.setHgap(10);
            grid.setVgap(10);

            TextField magazineNameField = new TextField();
            TextField isbnField = new TextField();
            TextField publisherField = new TextField();
            TextField yearField = new TextField();
            TextField monthField = new TextField();
            TextField totalCopiesField = new TextField();

            styleTextField(magazineNameField, "");
            styleTextField(isbnField, "");
            styleTextField(publisherField, "");
            styleTextField(yearField, "");
            styleTextField(monthField, "");
            styleTextField(totalCopiesField, "");

            Text validationText = new Text();
            isbnField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.matches("\\d*")) {  // Allow only digits
                    isbnField.setText(newValue.replaceAll("[^\\d]", ""));
                }
                if (newValue.length() != 13) {
                    validationText.setFill(Color.RED);
                    validationText.setText("ISBN must be exactly 13 digits");
                } else {
                    validationText.setFill(Color.GREEN);
                    validationText.setText("ISBN format is correct");
                }
            });

            Button submitButton = new Button("Upload Magazine");
            styleButton(submitButton);

            submitButton.setOnAction(e -> {
                System.out.println("Magazine Name: " + magazineNameField.getText());
                System.out.println("ISBN: " + isbnField.getText());
                System.out.println("Publisher: " + publisherField.getText());
                System.out.println("Year: " + yearField.getText());
                System.out.println("Month: " + monthField.getText());
                System.out.println("Total Copies: " + totalCopiesField.getText());

                base.uploadMagazine(documentId, magazineNameField.getText(), isbnField.getText(), publisherField.getText(),
                        yearField.getText(), monthField.getText(), totalCopiesField.getText());

                librarianMenu(primaryStage).handle(new ActionEvent());
            });

            // Layout the form
            grid.add(new Label("Magazine Name:"), 0, 0);
            grid.add(magazineNameField, 1, 0);
            grid.add(new Label("ISBN (13 digits):"), 0, 1);
            grid.add(isbnField, 1, 1);
            grid.add(validationText, 2, 1);
            grid.add(new Label("Publisher:"), 0, 2);
            grid.add(publisherField, 1, 2);
            grid.add(new Label("Year:"), 0, 3);
            grid.add(yearField, 1, 3);
            grid.add(new Label("Month:"), 0, 4);
            grid.add(monthField, 1, 4);
            grid.add(new Label("Total Copies:"), 0, 5);
            grid.add(totalCopiesField, 1, 5);
            grid.add(submitButton, 1, 6);
            grid.add(goBack, 1, 7);

            Scene scene = new Scene(grid, 800, 800);
            primaryStage.setTitle("Enter New Magazine Details");
            primaryStage.setScene(scene);
            primaryStage.show();
        };
    }


    EventHandler<ActionEvent> addArticles(Stage primaryStage, Integer documentId){
        return actionEvent -> {

            GridPane grid = new GridPane();
            grid.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
            grid.setAlignment(Pos.CENTER);
            grid.setHgap(10);
            grid.setVgap(10);

            // Define text fields for article information
            TextField articleTitleField = new TextField();
            TextField issueNumberField = new TextField();
            TextField volumeNumberField = new TextField();
            TextField yearField = new TextField();
            TextField pageNumbersField = new TextField();

            styleTextField(articleTitleField, "");
            styleTextField(issueNumberField, "");
            styleTextField(volumeNumberField, "");
            styleTextField(yearField, "");
            styleTextField(pageNumbersField, "");

            // Save button
            Button saveButton = new Button("Save");
            saveStyleButton(saveButton);

            saveButton.setOnAction(e -> {
                System.out.println("Article Saved:");
                System.out.println("Title: " + articleTitleField.getText());
                System.out.println("Issue Number: " + issueNumberField.getText());
                System.out.println("Volume Number: " + volumeNumberField.getText());
                System.out.println("Year: " + yearField.getText());
                System.out.println("Page Numbers: " + pageNumbersField.getText());


                base.addArticles(connection, documentId, articleTitleField.getText(), issueNumberField.getText(),
                        volumeNumberField.getText(), yearField.getText(), pageNumbersField.getText());

                saveButton.setDisable(true);  // Disable save button after saving
            });

            Button addAnotherButton = new Button("Add Another Article");
            saveStyleButton(addAnotherButton);

            addAnotherButton.setOnAction(e -> {
                articleTitleField.setText("");
                issueNumberField.setText("");
                volumeNumberField.setText("");
                yearField.setText("");
                pageNumbersField.setText("");
                saveButton.setDisable(false);  // Enable the save button
            });

            Button finishUploading = new Button("Finish Uploading");
            logoutStyle(finishUploading);
            finishUploading.setOnAction(librarianMenu(primaryStage));

            grid.add(new Label("Article Title:"), 0, 0);
            grid.add(articleTitleField, 1, 0);
            grid.add(new Label("Issue Number:"), 0, 1);
            grid.add(issueNumberField, 1, 1);
            grid.add(new Label("Volume Number:"), 0, 2);
            grid.add(volumeNumberField, 1, 2);
            grid.add(new Label("Year:"), 0, 3);
            grid.add(yearField, 1, 3);
            grid.add(new Label("Page Numbers:"), 0, 4);
            grid.add(pageNumbersField, 1, 4);
            grid.add(saveButton, 1, 5);
            grid.add(addAnotherButton, 1, 6);
            grid.add(finishUploading, 1, 7);

            Scene scene = new Scene(grid, 800, 800);
            primaryStage.setTitle("Enter Article Details");
            primaryStage.setScene(scene);
            primaryStage.show();

        };
    }

    EventHandler<ActionEvent> addJournal(Stage primaryStage){
        return actionEvent -> {

            Integer documentId = base.enterDocumentAndGetDocumentId("Journal");
            System.out.println("Here inside Journal");


            Button goBack = new Button("Go Back");
            logoutStyle(goBack);
            goBack.setOnAction(e -> {
                base.deleteADocument(connection, documentId);
                pickDocumentToAdd(primaryStage).handle(new ActionEvent());
            });


            GridPane grid = new GridPane();
            grid.setAlignment(Pos.CENTER);
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            TextField journalNameField = new TextField();
            styleTextField(journalNameField, "");
            grid.add(new Label("Journal Name:"), 0, 0);
            grid.add(journalNameField, 1, 0);

            TextField publisherField = new TextField();
            styleTextField(publisherField, "");
            grid.add(new Label("Publisher:"), 0, 1);
            grid.add(publisherField, 1, 1);

            TextField totalCopiesField = new TextField();
            styleTextField(totalCopiesField, "");
            grid.add(new Label("Total Copies:"), 0, 2);
            grid.add(totalCopiesField, 1, 2);

            Button addArticlesButton = new Button("Add Articles ->");
            styleButton(addArticlesButton);

            addArticlesButton.setOnAction(e -> {
                System.out.println("Journal Name: " + journalNameField.getText());
                System.out.println("Publisher: " + publisherField.getText());
                System.out.println("Total Copies: " + totalCopiesField.getText());

                base.uploadJournal(connection, documentId, journalNameField.getText(), publisherField.getText(),
                        totalCopiesField.getText());

                addArticles(primaryStage, documentId).handle(new ActionEvent());

            });
            grid.add(addArticlesButton, 1, 3);
            grid.add(goBack, 1, 4);

            Scene scene = new Scene(grid, 800, 800);
            primaryStage.setTitle("Enter New Journal Details");
            primaryStage.setScene(scene);
            primaryStage.show();

        };
    }


    EventHandler<ActionEvent> pickDocumentToAdd(Stage primaryStage){
        return actionEvent -> {

            ComboBox<String> insertDocumentComboBox = new ComboBox<>(
                    FXCollections.observableArrayList("Book", "Magazine", "Journal")
            );
            styleComboBox(insertDocumentComboBox, "Insert New Document");

            insertDocumentComboBox.setOnAction(e -> {
                if (insertDocumentComboBox.getValue() != null) {

                    if(insertDocumentComboBox.getValue() == "Magazine"){
                        addMagazine(primaryStage).handle(new ActionEvent());
                    }else if(insertDocumentComboBox.getValue() == "Book"){
                        addBook(primaryStage).handle(new ActionEvent());
                    }else if(insertDocumentComboBox.getValue() == "Journal"){
                        addJournal(primaryStage).handle(new ActionEvent());
                    }
                }
            });



            ComboBox<String> updateDocumentComboBox = new ComboBox<>(
                    FXCollections.observableArrayList("Book", "Magazine", "Journal")
            );
            styleComboBox(updateDocumentComboBox, "Update Existing Document");

            updateDocumentComboBox.setOnAction(e -> {
                if (updateDocumentComboBox.getValue() != null) {

//                    updateBooks

                    if(updateDocumentComboBox.getValue() == "Magazine"){
                        updateMagazine(primaryStage).handle(new ActionEvent());
                    }else if(updateDocumentComboBox.getValue() == "Book"){
                        updateBooks(primaryStage).handle(new ActionEvent());
                    }else if(updateDocumentComboBox.getValue() == "Journal"){
                        updateJournals(primaryStage).handle(new ActionEvent());
                    }

                }
            });

            Button goBack = new Button("Go Back");
            logoutStyle(goBack);

            goBack.setOnAction(librarianMenu(primaryStage));


            VBox root = new VBox(10, insertDocumentComboBox, updateDocumentComboBox, goBack);
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
            root.setAlignment(Pos.CENTER);

            Scene scene = new Scene(root, 800, 800);
            primaryStage.setTitle("Document Type Selector");
            primaryStage.setScene(scene);
            primaryStage.show();


        };
    }

    EventHandler<ActionEvent> updateClient(Stage primaryStage) {
        return event -> {
            VBox root = new VBox(10);
            root.setAlignment(Pos.CENTER);
            root.setSpacing(20);
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Map<String, String> nameAndEmail = base.getClientInformation();
            ComboBox<String> clientNamesComboBox = new ComboBox<>(FXCollections.observableArrayList(nameAndEmail.keySet()));
            styleComboBox(clientNamesComboBox, "View Clients to Edit");

            TextField nameField = new TextField();
            styleTextField(nameField, "Enter New Name");

            ComboBox<String> addressesComboBox = new ComboBox<>();
            styleComboBox(addressesComboBox, "Edit Address");

            TextField newAddressField = new TextField();
            styleTextField(newAddressField,"Enter Updated Address");

            Button saveNameButton = new Button("Save Name");
            saveStyleButton(saveNameButton);

            Button saveAddressButton = new Button("Save Address");
            saveStyleButton(saveAddressButton);

            ComboBox<String> creditCardsComboBox = new ComboBox<>();
            styleComboBox(creditCardsComboBox, "Choose Credit Card to Update");

            TextField newCreditCardField = new TextField();
            styleTextField(newCreditCardField, "Enter New Credit Card Number");

            ComboBox<String> billingAddressComboBox = new ComboBox<>();
            styleComboBox(billingAddressComboBox, "Choose Updated Billing Address");

            Button finishUpdatingButton = new Button("Finish Updating");
            saveStyleButton(finishUpdatingButton);

            nameField.setDisable(true);
            addressesComboBox.setDisable(true);
            newAddressField.setDisable(true);
            saveNameButton.setDisable(true);
            saveAddressButton.setDisable(true);
            newCreditCardField.setDisable(true);
            creditCardsComboBox.setDisable(true);
            billingAddressComboBox.setDisable(true);
            finishUpdatingButton.setDisable(true);


            final Map<String, Integer>[] addressMap = new Map[1]; // Array used for mutability within lambdas
            final Map<String, Integer>[] creditCardMap = new Map[1];
            final Map<String, Integer>[] newAddressMap = new Map[1];

            clientNamesComboBox.setOnAction(e -> {
                String selectedName = clientNamesComboBox.getSelectionModel().getSelectedItem();
                this.clientEmail = nameAndEmail.get(selectedName);

                nameField.setText(selectedName);
                nameField.setDisable(false);
                saveNameButton.setDisable(false);

                addressMap[0] = base.getClientAddresses(clientEmail);
                addressesComboBox.setItems(FXCollections.observableArrayList(addressMap[0].keySet()));
                addressesComboBox.setDisable(false);

                creditCardMap[0] = base.getClientCreditCards(clientEmail);
                creditCardsComboBox.setItems(FXCollections.observableArrayList(creditCardMap[0].keySet()));
                creditCardsComboBox.setDisable(false);
            });


            saveNameButton.setOnAction(e -> {
                if (!nameField.getText().isEmpty()) {

                    base.updateClientName(clientEmail, nameField.getText());
                    System.out.println("Name updated to: " + nameField.getText());
                    System.out.println("Email is : " + clientEmail);
                }
            });

            addressesComboBox.setOnAction(e -> {
                String selectedAddress = addressesComboBox.getSelectionModel().getSelectedItem();
                newAddressField.setText(selectedAddress);
                newAddressField.setDisable(false);
                saveAddressButton.setDisable(false);
            });

            saveAddressButton.setOnAction(e -> {
                String selectedAddress = addressesComboBox.getSelectionModel().getSelectedItem();
                if (selectedAddress != null && !newAddressField.getText().isEmpty()) {
                    Integer addressId = addressMap[0].get(selectedAddress);
                    System.out.println("Address updated to: " + newAddressField.getText());


                    base.updateAddress(addressId, newAddressField.getText());


                    newAddressMap[0] = base.getClientAddresses(clientEmail);
                    billingAddressComboBox.setItems(FXCollections.observableArrayList(newAddressMap[0].keySet()));
                    billingAddressComboBox.setDisable(false);
                }
            });

            creditCardsComboBox.setOnAction(e -> {
                newCreditCardField.setText(creditCardsComboBox.getSelectionModel().getSelectedItem());
                newCreditCardField.setDisable(false);
                billingAddressComboBox.setDisable(false);
                finishUpdatingButton.setDisable(false);
            });

            billingAddressComboBox.setOnAction(e -> {

                System.out.println("Email printed: " + clientEmail);
            });

            finishUpdatingButton.setOnAction(e -> {
                String oldCardNumber = creditCardsComboBox.getSelectionModel().getSelectedItem();
                String newCardNumber = newCreditCardField.getText();
                Integer addressId = newAddressMap[0].get(billingAddressComboBox.getSelectionModel().getSelectedItem());

                base.updateClientsCard(oldCardNumber, newCardNumber, addressId, clientEmail);

                System.out.println("Card updated to: " + newCardNumber + " with new address ID: " + addressId + "\n" + "Old card: " + oldCardNumber);

                clientAccountManagement(primaryStage).handle(new ActionEvent());

            });

            Button goBack = new Button("Go Back");
            logoutStyle(goBack);
            goBack.setOnAction(clientAccountManagement(primaryStage));

            root.getChildren().addAll(clientNamesComboBox, nameField, saveNameButton, addressesComboBox, newAddressField, saveAddressButton,
                    creditCardsComboBox, newCreditCardField, billingAddressComboBox, finishUpdatingButton, goBack);
            Scene scene = new Scene(root, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.show();
        };
    }


    EventHandler<ActionEvent> deleteClient(Stage primaryStage) {
        return event -> {
            VBox root = new VBox(10);
            root.setAlignment(Pos.CENTER);
            root.setSpacing(20);

            Map<String, String> nameAndEmail = base.getClientInformation();

            ComboBox<String> clientNames = new ComboBox<>();
            ObservableList<String> options = FXCollections.observableArrayList(nameAndEmail.keySet());
            clientNames.setItems(options);
            styleComboBox(clientNames, "Select a Client");

            Button viewAllClientsButton = new Button("View All Clients");
            styleButton(viewAllClientsButton);

            Button deleteButton = new Button("Delete Selected Client");
            styleButton(deleteButton);
            deleteButton.setDisable(true);

            Button back = new Button("Go Back");
            logoutStyle(back);
            back.setOnAction(clientAccountManagement(primaryStage));


            viewAllClientsButton.setOnAction(e -> {
                clientNames.show();
            });

            clientNames.setOnAction(e -> {
                deleteButton.setDisable(false);
            });

            deleteButton.setOnAction(e -> {
                String selectedName = clientNames.getSelectionModel().getSelectedItem();
                if (selectedName != null) {
                    String email = nameAndEmail.get(selectedName);
                    base.deleteClientByEmail(email);  // Assuming this method deletes the client by email
                    clientNames.getItems().remove(selectedName); // Update UI
                    deleteButton.setDisable(true); // Disable button until next selection
                    System.out.println("Deleted client: " + selectedName + " with email: " + email);

                    EventHandler<ActionEvent> goBack = clientAccountManagement(primaryStage);
                    goBack.handle(new ActionEvent());
                }
            });

            root.getChildren().addAll(viewAllClientsButton, clientNames, deleteButton, back);
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");
            Scene scene = new Scene(root, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.show();
        };
    }

    EventHandler<ActionEvent> addClientCreditCards(Stage primaryStage, String clientEmail) {
        return event -> {
            VBox root = new VBox(10);
            root.setAlignment(Pos.CENTER);
            root.setSpacing(10);
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            TextField creditCardField = new TextField();
            styleTextField(creditCardField, "Enter 16-digit credit card number");

            Button chooseAddressButton = new Button("Choose Billing Address");
            styleButton(chooseAddressButton);

            ComboBox<String> addressComboBox = new ComboBox<>();
            styleComboBox(addressComboBox, "");

            Button saveButton = new Button("Save");
            saveStyleButton(saveButton);

            Button addAnotherButton = new Button("Add Another Credit Card");
            saveStyleButton(addAnotherButton);

            // Initially, disable some components
            chooseAddressButton.setDisable(true);
            saveButton.setDisable(true);
            addressComboBox.setDisable(true);
            addAnotherButton.setVisible(false);

            creditCardField.textProperty().addListener((obs, oldVal, newVal) -> {
                chooseAddressButton.setDisable(newVal.length() != 16);
            });

            chooseAddressButton.setOnAction(e -> {
                Map<String, Integer> addresses = base.getClientAddresses(clientEmail);
                ObservableList<String> addressOptions = FXCollections.observableArrayList(addresses.keySet());
                addressComboBox.setItems(addressOptions);
                addressComboBox.setDisable(false);
            });

            addressComboBox.setOnAction(e -> {
                saveButton.setDisable(false);
            });

            saveButton.setOnAction(e -> {
                String selectedAddress = addressComboBox.getSelectionModel().getSelectedItem();
                if (selectedAddress != null) {
                    Integer addressId = base.getClientAddresses(clientEmail).get(selectedAddress);
                    base.addCreditCard(clientEmail, creditCardField.getText(), addressId);
                    addAnotherButton.setVisible(true);
                }
            });

            addAnotherButton.setOnAction(e -> {
                creditCardField.clear();
                addressComboBox.getItems().clear();
                addressComboBox.setDisable(true);
                saveButton.setDisable(true);
                addAnotherButton.setVisible(false);
                chooseAddressButton.fire();
            });

            Button finishButton = new Button("Main Menu");
            logoutStyle(finishButton);
            finishButton.setOnAction(clientAccountManagement(primaryStage));

            root.getChildren().addAll(creditCardField, chooseAddressButton, addressComboBox, saveButton, addAnotherButton, finishButton);

            Scene scene = new Scene(root, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.show();
        };
    }


    EventHandler<ActionEvent> registerNewClient(Stage primaryStage) {
        return event -> {

            VBox root = new VBox(20);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(20));
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            TextField nameField = new TextField();
            styleTextField(nameField, "Enter name");
//            nameField.setPromptText("Enter name");

            TextField emailField = new TextField();
            styleTextField(emailField, "Enter email address");
//            emailField.setPromptText("Enter email address");

            PasswordField passwordField = new PasswordField();
            stylePasswordField(passwordField, "Enter Password");
//            passwordField.setPromptText("Enter password");

            VBox addressContainer = new VBox(10);
            ObservableList<TextField> addressFields = FXCollections.observableArrayList();

            Button addAddressButton = new Button("Add Address");
            styleButton(addAddressButton);
            addAddressButton.setOnAction(e -> {
                TextField newAddressField = new TextField();
                styleTextField(newAddressField, "");
                newAddressField.setPromptText("Enter address");
                addressFields.add(newAddressField);
                addressContainer.getChildren().add(newAddressField);
            });

            Button addPaymentMethodButton = new Button("Continue to Payment");
            styleButton(addPaymentMethodButton);

            addPaymentMethodButton.setOnAction(e -> {
                base.addClientInformation(nameField.getText(), emailField.getText(), passwordField.getText());
                base.addClientAddresses(emailField.getText(), addressFields);

                EventHandler<ActionEvent> addCardsHandler = addClientCreditCards(primaryStage, emailField.getText());
                addCardsHandler.handle(new ActionEvent());
            });

            HBox navigationButtons = new HBox(10);
            navigationButtons.setAlignment(Pos.CENTER);
            Button goBackButton = new Button("Go Back");
            logoutStyle(goBackButton);

            goBackButton.setOnAction(clientAccountManagement(primaryStage));

            Button logoutButton = new Button("Logout");
            logoutStyle(logoutButton);
            logoutButton.setOnAction(mainMenu(primaryStage));

            navigationButtons.getChildren().addAll(goBackButton, logoutButton);

            root.getChildren().addAll(nameField, emailField, passwordField, addAddressButton, addressContainer, addPaymentMethodButton, navigationButtons);

            Scene scene = new Scene(root, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.show();

        };
    }


    // Options to create, update, and delete client account
    EventHandler<ActionEvent> clientAccountManagement(Stage primaryStage) {
        return event -> {

            VBox root = new VBox(20);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(25));
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Button registerClientsButton = new Button("Register New Clients");
            styleButton(registerClientsButton);
            registerClientsButton.setOnAction(registerNewClient(primaryStage));

            Button updateClientInfoButton = new Button("Update Client Information");
            styleButton(updateClientInfoButton);
            updateClientInfoButton.setOnAction(updateClient(primaryStage));

            Button deleteClientButton = new Button("Delete a Client");
            styleButton(deleteClientButton);
            deleteClientButton.setOnAction(deleteClient(primaryStage));

            HBox lowerButtons = new HBox(10);
            lowerButtons.setAlignment(Pos.CENTER);

            Button goBackButton = new Button("Go Back");
            logoutStyle(goBackButton);
            goBackButton.setOnAction(librarianMenu(primaryStage));

            Button logoutButton = new Button("Logout");
            logoutStyle(logoutButton);
            logoutButton.setOnAction(e -> {
                mainMenu(primaryStage).handle(new ActionEvent());  // Assuming mainMenu sets up the main menu
            });

            lowerButtons.getChildren().addAll(goBackButton, logoutButton);

            root.getChildren().addAll(registerClientsButton, updateClientInfoButton, deleteClientButton, lowerButtons);

            Scene scene = new Scene(root, 800, 800);
            primaryStage.setScene(scene);

        };
    }


    // When librarian logs in. It will be presented with the options to manage
    // Clients and Documents
    private EventHandler<ActionEvent> librarianMenu(Stage primaryStage) {
        return event -> {
            VBox root = new VBox(20);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            Button manageDocumentsButton = new Button("Manage Documents");
            styleButton(manageDocumentsButton);
            manageDocumentsButton.setOnAction(pickDocumentToAdd(primaryStage));

            Button manageClientsButton = new Button("Manage Clients");
            styleButton(manageClientsButton);
            manageClientsButton.setOnAction(clientAccountManagement(primaryStage));

            Button logoutButton = new Button("Logout");
            logoutStyle(logoutButton);
            logoutButton.setOnAction(e -> {
                mainMenu(primaryStage).handle(new ActionEvent());
            });

            root.getChildren().addAll(manageDocumentsButton, manageClientsButton, logoutButton);

            Scene scene = new Scene(root, 800, 800);
            primaryStage.setScene(scene);
        };
    }


    // here we prompt librarian to attempt logging in
    // We will check the database for verification
    // Database will return true or false
    // If true user will be logged in
    EventHandler<ActionEvent> librarianAuthentication(Stage primaryStage) {
        return event -> {
            VBox root = new VBox(20);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setAlignment(Pos.CENTER);

            TextField emailField = new TextField();
            styleTextField(emailField, "Enter your email");

            PasswordField passwordField = new PasswordField();
            stylePasswordField(passwordField, "Enter your password");

            Button loginButton = new Button("Login");
            styleButton(loginButton);

            loginButton.setOnAction(e -> {
                if(base.verifyLibrarianLoginAttempt(emailField.getText(), passwordField.getText())){
                    System.out.println("Login Successful");
                    librarianMenu(primaryStage).handle(new ActionEvent());
                }
            });

            Button goBackButton = new Button("Go Back");
            logoutStyle(goBackButton);
            goBackButton.setOnAction(e -> {
                mainMenu(primaryStage).handle(new ActionEvent());
            });

            grid.add(new Label("Email:"), 0, 0);
            grid.add(emailField, 1, 0);
            grid.add(new Label("Password:"), 0, 1);
            grid.add(passwordField, 1, 1);

            root.getChildren().addAll(grid, loginButton, goBackButton);

            Scene scene = new Scene(root, 800, 800);
            primaryStage.setScene(scene);
            primaryStage.show();
        };
    }


    // When librarian decides to register, it will be prompted to this
    private EventHandler<ActionEvent> registeringLibrarian(Stage primaryStage) {
        return event -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Register Librarian");
            dialog.setHeaderText("Enter librarian details");

            ButtonType registerButtonType = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(registerButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setAlignment(Pos.CENTER);

            TextField ssnField = new TextField();
            ssnField.setPromptText("SSN");
            TextField nameField = new TextField();
            nameField.setPromptText("Name");
            TextField emailField = new TextField();
            emailField.setPromptText("Email");
            TextField salaryField = new TextField();
            salaryField.setPromptText("Salary");
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("Password");

            grid.add(new Label("SSN:"), 0, 0);
            grid.add(ssnField, 1, 0);
            grid.add(new Label("Name:"), 0, 1);
            grid.add(nameField, 1, 1);
            grid.add(new Label("Email:"), 0, 2);
            grid.add(emailField, 1, 2);
            grid.add(new Label("Salary:"), 0, 3);
            grid.add(salaryField, 1, 3);
            grid.add(new Label("Password:"), 0, 4);
            grid.add(passwordField, 1, 4);

            dialog.getDialogPane().setContent(grid);

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == registerButtonType) {
                    BigDecimal salary = new BigDecimal(salaryField.getText());  // Convert salary text to BigDecimal
                    base.registerLibrarian(ssnField.getText(), nameField.getText(), emailField.getText(), salary, passwordField.getText());
                }
                return null;
            });

            dialog.showAndWait();
        };
    }


    // This is where the user has the option to pick either:
    // 1. Register as Librarian
    // 2. Login as Librarian
    // 3. Login as Client
    EventHandler<ActionEvent> mainMenu(Stage primaryStage) {
        EventHandler<ActionEvent> menu = new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {

                base.connectDatabase();

                VBox root = new VBox(20);
                root.setAlignment(Pos.CENTER);
                root.setStyle("-fx-background-color: linear-gradient(to bottom, #33ccff, #ff99cc);");

                Button signUpBtn = new Button("Register as Librarian");
                styleButton(signUpBtn);
                signUpBtn.setOnAction(registeringLibrarian(primaryStage));

                Button signInLibrarianBtn = new Button("I'm a Librarian");
                styleButton(signInLibrarianBtn);
                signInLibrarianBtn.setOnAction(librarianAuthentication(primaryStage));


                Button signInClientBtn = new Button("I'm a Client");
                styleButton(signInClientBtn);
                signInClientBtn.setOnAction(clientAuthentication(primaryStage));

                root.getChildren().addAll(signUpBtn, signInLibrarianBtn, signInClientBtn);

                Scene scene = new Scene(root, 800, 800);
                primaryStage.setScene(scene);
            }
        };
        return menu;
    }

    @Override
    public void start(Stage primaryStage) {

        connection = base.establishConnectionToDatabase();

        Button enterLibraryBtn = new Button("Enter Library");
        styleButton(enterLibraryBtn);
        enterLibraryBtn.setOnAction(mainMenu(primaryStage));

        StackPane root = new StackPane();
        root.getChildren().add(enterLibraryBtn);
        root.setStyle("-fx-background-color: linear-gradient(to top, #ffccff, #66ffff);");

        Scene entryScene = new Scene(root, 800, 800);

        primaryStage.setTitle("Library Entrance");
        primaryStage.setScene(entryScene);
        primaryStage.show();
    }











    //-------------------------CSS-----Styling buttons below-------------------------------
    private void styleButton(Button button) {

        button.setStyle(null);

        button.setStyle("-fx-background-color: linear-gradient(to bottom right, #d28674 50%, #FEB47B 100%); " +
                "-fx-background-radius: 5; " +
                "-fx-text-fill: rgba(0,0,0,0.58); " +
                "-fx-font-size: 16px; " +
                "-fx-border-color: black; " +
                "-fx-border-width: 2px; " +
                "-fx-padding: 10px;");
        button.setMinWidth(150);
    }

    private void saveStyleButton(Button button) {
        button.setStyle(null);

        button.setStyle("-fx-background-color: linear-gradient(to bottom right, #c5d274 50%, #7bfec7 100%); " +
                "-fx-background-radius: 5; " +
                "-fx-text-fill: rgba(0,0,0,0.58); " +
                "-fx-font-size: 10px; " +
                "-fx-border-color: black; " +
                "-fx-border-width: 2px; " +
                "-fx-padding: 5px;");
        button.setMinWidth(100);
    }

    private void logoutStyle(Button button) {
        button.setStyle(null);

        button.setStyle("-fx-background-color: linear-gradient(to bottom right, #74d2ca 50%, #FEB47B 100%); " +
                "-fx-background-radius: 5; " +
                "-fx-text-fill: rgba(0,0,0,0.58); " +
                "-fx-font-size: 16px; " +
                "-fx-border-color: black; " +
                "-fx-border-width: 2px; " +
                "-fx-padding: 5px;");
        button.setMinWidth(100);
    }

    private void styleComboBox(ComboBox<String> comboBox, String prompt) {
        comboBox.setStyle("-fx-background-color: linear-gradient(to bottom right, #d28674, #7bd7fe); " +
                "-fx-background-radius: 5; " +
                "-fx-text-fill: black; " +
                "-fx-font-size: 14px; " +
                "-fx-border-color: black; " +
                "-fx-border-width: 2px; " +
                "-fx-padding: 5px; " +
                "-fx-prompt-text-fill: derive(-fx-control-inner-background, -30%);");

        comboBox.setPromptText(prompt);
    }

    public void styleTextField(TextField textField, String promptText) {
        textField.setStyle("-fx-background-color: linear-gradient(to bottom right, #d28674, #FEB47B); " +
                "-fx-background-radius: 5; " +
                "-fx-text-fill: black; " +
                "-fx-font-size: 14px; " +
                "-fx-border-color: black; " +
                "-fx-border-width: 2px; " +
                "-fx-padding: 5px;" +
                "-fx-prompt-text-fill: rgba(211,211,211,0.99);");
        textField.setPromptText(promptText);
        textField.setPrefWidth(150);
    }

    public void stylePasswordField(PasswordField passwordField, String promptText) {
        passwordField.setStyle("-fx-background-color: linear-gradient(to bottom right, #d28674, #FEB47B); " +
                "-fx-background-radius: 5; " +
                "-fx-text-fill: black; " +
                "-fx-font-size: 14px; " +
                "-fx-border-color: black; " +
                "-fx-border-width: 2px; " +
                "-fx-padding: 5px;" +
                "-fx-prompt-text-fill: rgba(211,211,211,0.99);");
        passwordField.setPromptText(promptText);
        passwordField.setPrefWidth(200);
    }



    public static void main(String[] args) {

        launch(args);
    }
}

