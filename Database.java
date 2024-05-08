import javafx.collections.ObservableList;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;


public class Database {


    public Integer getBorrowId(Connection connection, Integer documentId, String emailOfClient) {
        String sql = "SELECT borrow_id FROM Borrow WHERE document_id = ? AND client_email = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, documentId);
            stmt.setString(2, emailOfClient);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("borrow_id");
                } else {
                    System.out.println("No matching record found.");
                    return null;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching borrow_id: " + e.getMessage());
            return null;
        }
    }


    public void returnAndUpdateCopies(Connection connection, Integer documentId, Integer copiesReturned) {
        String typeQuery = "SELECT document_type FROM Documents WHERE document_id = ?";
        String documentType = null;

        try (PreparedStatement typeStmt = connection.prepareStatement(typeQuery)) {
            typeStmt.setInt(1, documentId);
            try (ResultSet rs = typeStmt.executeQuery()) {
                if (rs.next()) {
                    documentType = rs.getString("document_type");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching document type: " + e.getMessage());
            return;
        }

        if (documentType == null) {
            System.out.println("No document found with ID: " + documentId);
            return;
        }

        String updateQuery = null;
        switch (documentType) {
            case "Book":
                updateQuery = "UPDATE Books SET total_copies_lended = total_copies_lended - ? WHERE document_id = ?";
                break;
            case "Magazine":
                updateQuery = "UPDATE Magazines SET total_copies_lended = total_copies_lended - ? WHERE document_id = ?";
                break;
            case "Journal":
                updateQuery = "UPDATE Journals SET total_copies_lended = total_copies_lended - ? WHERE document_id = ?";
                break;
            default:
                System.out.println("Unknown document type: " + documentType);
                return;
        }

        try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
            updateStmt.setInt(1, copiesReturned);
            updateStmt.setInt(2, documentId);

            int affectedRows = updateStmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Updated total_copies_lended successfully for document ID: " + documentId);
            } else {
                System.out.println("No rows affected. Check if the document ID exists and the copies returned does not exceed the lended copies.");
            }
        } catch (SQLException e) {
            System.out.println("Error updating total_copies_lended: " + e.getMessage());
        }
    }


    public int deleteBorrowTableRecord(Connection connection, String clientEmail, Integer documentId) {
        int copiesLended = 0;

        String selectSQL = "SELECT copies_lended FROM Borrow WHERE client_email = ? AND document_id = ?";

        String deleteSQL = "DELETE FROM Borrow WHERE client_email = ? AND document_id = ?";

        try {

            connection.setAutoCommit(false);

            try (PreparedStatement selectStmt = connection.prepareStatement(selectSQL)) {
                selectStmt.setString(1, clientEmail);
                selectStmt.setInt(2, documentId);

                ResultSet rs = selectStmt.executeQuery();
                if (rs.next()) {
                    copiesLended = rs.getInt("copies_lended");
                } else {
                    connection.rollback();
                    System.out.println("No record found to delete.");
                    return 0;
                }
            }

            try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSQL)) {
                deleteStmt.setString(1, clientEmail);
                deleteStmt.setInt(2, documentId);

                int affectedRows = deleteStmt.executeUpdate();
                if (affectedRows == 0) {
                    connection.rollback();
                    System.out.println("No record deleted.");
                    return 0;
                }

                connection.commit();
            }
        } catch (SQLException e) {

            try {
                connection.rollback();
            } catch (SQLException ex) {
                System.out.println("Error rolling back transaction: " + ex.getMessage());
            }
            System.out.println("Error processing delete operation: " + e.getMessage());
            return 0;
        } finally {

            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                System.out.println("Error resetting auto-commit: " + e.getMessage());
            }
        }

        return copiesLended;
    }

    public BigDecimal moneyDue(Connection connection, Integer documentId) {

        String sql = "SELECT SUM(amount_owed) AS total_due FROM Transaction WHERE borrow_id = ? AND payment_status = FALSE";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setInt(1, documentId);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    BigDecimal totalDue = rs.getBigDecimal("total_due");
                    if (totalDue != null) {
                        return totalDue;
                    } else {

                        return BigDecimal.ZERO;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error calculating money due: " + e.getMessage());
        }

        return null;
    }

    public void updateTransactionTable(Connection connection, Integer documentId, Integer weeksLate, Integer borrowId) {

        String sql = "INSERT INTO Transaction (borrow_id, amount_owed, payment_status) VALUES (?, ?, FALSE)";

        BigDecimal amountOwed = BigDecimal.valueOf(weeksLate * 5);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, borrowId);
            stmt.setBigDecimal(2, amountOwed);

            int rowsAffected = stmt.executeUpdate();
            System.out.println("Inserted " + rowsAffected + " rows into Transaction table.");
        } catch (SQLException e) {
            System.out.println("Error updating Transaction table: " + e.getMessage());
        }
    }

    

    public Integer calculateWeeksLate(Connection connection, Integer documentId, Date returnDate) {
        String query = "SELECT due_date FROM Borrow WHERE document_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, documentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Date dueDate = rs.getDate("due_date");
                    LocalDate dueLocalDate = convertToLocalDate(dueDate);
                    LocalDate returnLocalDate = convertToLocalDate(returnDate);

                    if (dueLocalDate != null && returnLocalDate != null) {
                        long weeksLate = java.time.temporal.ChronoUnit.WEEKS.between(dueLocalDate, returnLocalDate);

                        if((int) weeksLate <= 0){
                            return 0;
                        }

                        return (int) weeksLate;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error accessing Borrow table: " + e.getMessage());
        }
        System.out.println("No data found for document ID: " + documentId);
        return null;
    }

    private LocalDate convertToLocalDate(Date date) {
        if (date != null) {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTime(date);
            return LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
        }
        return null;
    }



    public Map<String, Integer> displayAllBorrowedDocuments(Connection connection, String clientEmail) {
        Map<String, Integer> borrowedDocuments = new HashMap<>();

        String sql = "SELECT document_id FROM Borrow WHERE client_email = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, clientEmail);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int documentId = rs.getInt("document_id");
                String documentType = getDocumentType(connection, documentId);

                if (documentType != null) {
                    String title = getDocumentTitleByType(connection, documentId, documentType);
                    if (title != null) {
                        borrowedDocuments.put(title, documentId);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving borrowed documents: " + e.getMessage());
        }

        return borrowedDocuments;
    }

    private String getDocumentTitleByType(Connection connection, Integer documentId, String documentType) {
        String query = "";
        if ("Book".equals(documentType)) {
            query = "SELECT title FROM Books WHERE document_id = ?";
        } else if ("Magazine".equals(documentType)) {
            query = "SELECT magazine_name AS title FROM Magazines WHERE document_id = ?";
        } else if ("Journal".equals(documentType)) {
            query = "SELECT journal_name AS title FROM Journals WHERE document_id = ?";
        }

        if (!query.isEmpty()) {
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setInt(1, documentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("title");
                    }
                }
            } catch (SQLException e) {
                System.out.println("Error retrieving document title: " + e.getMessage());
            }
        }

        return null;
    }




    public void updateBorrowTable(Connection connection, Integer documentId, String clientEmail, Integer lendingCopies) {

        String sql = "INSERT INTO Borrow (document_id, client_email, start_date, due_date, copies_lended) " +
                "VALUES (?, ?, ?, ?, ?)";


        LocalDate startDate = LocalDate.now();
        LocalDate dueDate = startDate.plusWeeks(4);

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, documentId);
            pstmt.setString(2, clientEmail);
            pstmt.setDate(3, java.sql.Date.valueOf(startDate));
            pstmt.setDate(4, java.sql.Date.valueOf(dueDate));
            pstmt.setInt(5, lendingCopies);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Successfully updated Borrow table.");
            } else {
                System.out.println("No rows affected.");
            }
        } catch (SQLException e) {
            System.out.println("Error updating Borrow table: " + e.getMessage());
        }
    }

    public void insertCopiesLended(Connection connection, Integer documentId, Integer lendingCopies) {

        String documentType = getDocumentType(connection, documentId);
        if (documentType == null) {
            System.out.println("No document found with ID: " + documentId);
            return;
        }


        String updateQuery = "";
        switch (documentType) {
            case "Book":
                updateQuery = "UPDATE Books SET total_copies_lended = total_copies_lended + ? WHERE document_id = ?";
                break;
            case "Magazine":
                updateQuery = "UPDATE Magazines SET total_copies_lended = total_copies_lended + ? WHERE document_id = ?";
                break;
            case "Journal":
                updateQuery = "UPDATE Journals SET total_copies_lended = total_copies_lended + ? WHERE document_id = ?";
                break;
            default:
                System.out.println("Invalid document type for document ID: " + documentId);
                return;
        }


        try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
            stmt.setInt(1, lendingCopies);
            stmt.setInt(2, documentId);
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                System.out.println("No rows updated, check if document ID exists and is correct: " + documentId);
            } else {
                System.out.println("Updated " + affectedRows + " rows successfully.");
            }
        } catch (SQLException e) {
            System.out.println("Error updating total_copies_lended: " + e.getMessage());
        }
    }

    private String getDocumentType(Connection connection, Integer documentId) {
        String query = "SELECT document_type FROM Documents WHERE document_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, documentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("document_type");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving document type: " + e.getMessage());
        }
        return null;
    }

    public Integer retrieveCopies(Connection connection, Integer documentId) {

        String query = "SELECT d.document_type, " +
                "CASE " +
                "WHEN d.document_type = 'Book' THEN (b.total_copies - b.total_copies_lended) " +
                "WHEN d.document_type = 'Magazine' THEN (m.total_copies - m.total_copies_lended) " +
                "WHEN d.document_type = 'Journal' THEN (j.total_copies - j.total_copies_lended) " +
                "ELSE 0 " +
                "END AS available_copies " +
                "FROM Documents d " +
                "LEFT JOIN Books b ON d.document_id = b.document_id " +
                "LEFT JOIN Magazines m ON d.document_id = m.document_id " +
                "LEFT JOIN Journals j ON d.document_id = j.document_id " +
                "WHERE d.document_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(documentId.toString()));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("available_copies");
                } else {
                    System.out.println("No document found with ID: " + documentId);
                    return null;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving available copies: " + e.getMessage());
            return null;
        }
    }


    public static Map<String, Integer> combineAndSortResults(Map<String, Integer> booksFound,
                                                             Map<String, Integer> magazinesFound,
                                                             Map<String, Integer> journalsFound,
                                                             String search) {
        Map<String, Integer> combinedResults = new HashMap<>();
        Map<String, Integer> sortedResults = new LinkedHashMap<>();

        combinedResults.putAll(booksFound);
        combinedResults.putAll(magazinesFound);
        combinedResults.putAll(journalsFound);

        String[] words = search.toLowerCase().split("\\s+");

        sortedResults = combinedResults.entrySet().stream()
                .sorted((entry1, entry2) -> Integer.compare(countMatches(entry2.getKey(), words), countMatches(entry1.getKey(), words)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));

        return sortedResults;
    }

    private static int countMatches(String text, String[] words) {
        int count = 0;
        text = text.toLowerCase();
        for (String word : words) {
            if (text.contains(word)) {
                count++;
            }
        }
        return count;
    }



    //-------------

    public static Map<String, Integer> clientSearchJournal(Connection connection, String search) {

        Map<Integer, String> journalDetails = new HashMap<>();
        Map<Integer, List<String>> articleDetails = new HashMap<>();
        Map<String, Integer> results = new LinkedHashMap<>();

        try {
            String[] words = search.toLowerCase().split("\\s+");


            for (String word : words) {
                String sql = "SELECT j.document_id, j.journal_name, j.publisher, j.total_copies, j.total_copies_lended, a.article_title, a.issue_number, a.volume_number " +
                        "FROM Journals j " +
                        "JOIN Articles a ON j.document_id = a.journal_id " +
                        "WHERE LOWER(j.journal_name) LIKE ? OR LOWER(j.publisher) LIKE ? OR CAST(j.total_copies AS TEXT) LIKE ? " +
                        "OR LOWER(a.article_title) LIKE ? OR CAST(a.issue_number AS TEXT) LIKE ? OR CAST(a.volume_number AS TEXT) LIKE ?";
                PreparedStatement statement = connection.prepareStatement(sql);

                String likePattern = "%" + word + "%";
                for (int i = 1; i <= 6; i++) {
                    statement.setString(i, likePattern);
                }

                ResultSet rs = statement.executeQuery();

                while (rs.next()) {
                    int documentId = rs.getInt("document_id");
                    String journalName = rs.getString("journal_name");
                    String publisher = rs.getString("publisher");
                    int totalCopies = rs.getInt("total_copies");
                    int total_copies_lended = rs.getInt("total_copies_lended");
                    String articleTitle = rs.getString("article_title");
                    int issueNumber = rs.getInt("issue_number");
                    int volumeNumber = rs.getInt("volume_number");


                    String baseKey = journalName + " by " + publisher + "\nTotal # of available copies: " + (totalCopies-total_copies_lended) + "\n";
                    journalDetails.putIfAbsent(documentId, baseKey);


                    String articleInfo = "\nArticle: " + articleTitle +
                            "\nIssue Number: " + issueNumber +
                            "\nVolume Number: " + volumeNumber + "\n";
                    articleDetails.computeIfAbsent(documentId, k -> new ArrayList<>()).add(articleInfo);
                }
            }


            for (Map.Entry<Integer, List<String>> entry : articleDetails.entrySet()) {
                Integer docId = entry.getKey();
                String journalBaseInfo = journalDetails.get(docId);
                StringBuilder allArticles = new StringBuilder(journalBaseInfo);
                for (String article : entry.getValue()) {
                    allArticles.append(article);
                }
                results.put(allArticles.toString(), docId);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return results;
    }


    public static Map<String, Integer> clientSearchMagazines(Connection connection, String search) {

        Map<String, Map.Entry<Integer, Integer>> matchCounts = new HashMap<>();
        Map<String, Integer> sortedResults = new LinkedHashMap<>();

        try {

            String[] words = search.toLowerCase().split("\\s+");


            for (String word : words) {
                String sql = "SELECT document_id, magazine_name, isbn, publisher, total_copies, total_copies_lended FROM Magazines WHERE " +
                        "LOWER(magazine_name) LIKE ? OR LOWER(isbn) LIKE ? OR LOWER(publisher) LIKE ?";
                PreparedStatement statement = connection.prepareStatement(sql);


                String likePattern = "%" + word + "%";
                for (int i = 1; i <= 3; i++) {
                    statement.setString(i, likePattern);
                }

                ResultSet rs = statement.executeQuery();

                while (rs.next()) {
                    int documentId = rs.getInt("document_id");
                    String magazineName = rs.getString("magazine_name");
                    String isbn = rs.getString("isbn");
                    String publisher = rs.getString("publisher");
                    int totalCopies = rs.getInt("total_copies");
                    int total_copies_lended = rs.getInt("total_copies_lended");
                    String key = magazineName + " by " + publisher +
                            "\nISBN#: " + isbn +
                            "\nTotal copies available: " + (totalCopies - total_copies_lended);

                    matchCounts.putIfAbsent(key, new AbstractMap.SimpleEntry<>(documentId, 0));
                    Map.Entry<Integer, Integer> currentEntry = matchCounts.get(key);
                    matchCounts.put(key, new AbstractMap.SimpleEntry<>(documentId, currentEntry.getValue() + 1));
                }
            }

            sortedResults = matchCounts.entrySet()
                    .stream()
                    .sorted((entry1, entry2) -> entry2.getValue().getValue().compareTo(entry1.getValue().getValue()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getKey(),
                            (e1, e2) -> e1, LinkedHashMap::new));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return sortedResults;
    }

    public static Map<String, Integer> clientSearchBooks(Connection connection, String search) {

        Map<String, Map.Entry<Integer, Integer>> matchCounts = new HashMap<>();
        Map<String, Integer> sortedResults = new LinkedHashMap<>();

        try {

            String[] words = search.toLowerCase().split("\\s+");

            for (String word : words) {
                String sql = "SELECT document_id, title, authors, isbn, publisher, total_copies, total_copies_lended FROM Books WHERE " +
                        "LOWER(title) LIKE ? OR LOWER(authors) LIKE ? OR LOWER(isbn) LIKE ? OR LOWER(publisher) LIKE ?";
                PreparedStatement statement = connection.prepareStatement(sql);

                String likePattern = "%" + word + "%";
                for (int i = 1; i <= 4; i++) {
                    statement.setString(i, likePattern);
                }

                ResultSet rs = statement.executeQuery();

                while (rs.next()) {
                    int documentId = rs.getInt("document_id");
                    String title = rs.getString("title");
                    String authors = rs.getString("authors");
                    String isbn = rs.getString("isbn");
                    String publisher = rs.getString("publisher");
                    int totalCopies = rs.getInt("total_copies");
                    int total_copies_lended = rs.getInt("total_copies_lended");
                    String key = title + " by " + authors +
                            "\nPublishers: " + publisher +
                            "\nISBN#: " + isbn +
                            "\nTotal copies available: " + (totalCopies - total_copies_lended);

                    matchCounts.putIfAbsent(key, new AbstractMap.SimpleEntry<>(documentId, 0));
                    Map.Entry<Integer, Integer> currentEntry = matchCounts.get(key);
                    matchCounts.put(key, new AbstractMap.SimpleEntry<>(documentId, currentEntry.getValue() + 1));
                }
            }

            sortedResults = matchCounts.entrySet()
                    .stream()
                    .sorted((entry1, entry2) -> entry2.getValue().getValue().compareTo(entry1.getValue().getValue()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getKey(),
                            (e1, e2) -> e1, LinkedHashMap::new));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return sortedResults;
    }

//    public static Map<String, Integer> clientSearchBooks(Connection connection, String search) {
//
//        Map<String, Map.Entry<Integer, Integer>> matchCounts = new HashMap<>();
//        Map<String, Integer> sortedResults = new LinkedHashMap<>();
//
//        try {
//
//            String[] words = search.toLowerCase().split("\\s+");
//
//            for (String word : words) {
//                String sql = "SELECT document_id, title, authors, isbn, publisher, total_copies FROM Books WHERE " +
//                        "LOWER(title) LIKE ? OR LOWER(authors) LIKE ? OR LOWER(isbn) LIKE ? OR LOWER(publisher) LIKE ?";
//                PreparedStatement statement = connection.prepareStatement(sql);
//
//                String likePattern = "%" + word + "%";
//                for (int i = 1; i <= 4; i++) {
//                    statement.setString(i, likePattern);
//                }
//
//                ResultSet rs = statement.executeQuery();
//
//                while (rs.next()) {
//                    int documentId = rs.getInt("document_id");
//                    String title = rs.getString("title");
//                    String authors = rs.getString("authors");
//                    String isbn = rs.getString("isbn");
//                    String publisher = rs.getString("publisher");
//                    int totalCopies = rs.getInt("total_copies");
//                    String key = title + " by " + authors +
//                            "\nPublishers: " + publisher +
//                            "\nISBN#: " + isbn +
//                            "\nTotal copies available: " + totalCopies;
//
//                    matchCounts.putIfAbsent(key, new AbstractMap.SimpleEntry<>(documentId, 0));
//                    Map.Entry<Integer, Integer> currentEntry = matchCounts.get(key);
//                    matchCounts.put(key, new AbstractMap.SimpleEntry<>(documentId, currentEntry.getValue() + 1));
//                }
//            }
//
//            sortedResults = matchCounts.entrySet()
//                    .stream()
//                    .sorted((entry1, entry2) -> entry2.getValue().getValue().compareTo(entry1.getValue().getValue()))
//                    .collect(Collectors.toMap(
//                            Map.Entry::getKey,
//                            entry -> entry.getValue().getKey(),
//                            (e1, e2) -> e1, LinkedHashMap::new));
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//
//        return sortedResults;
//    }


    boolean verifyClientLoginAttempt(Connection connection, String emailAddress, String password) {

        String query = "SELECT password FROM Clients WHERE email = ?";

        try {

            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, emailAddress);

            ResultSet resultSet = stmt.executeQuery();


            if (resultSet.next()) {

                String storedPassword = resultSet.getString("password");


                if (storedPassword.equals(password)) {
                    return true;
                }
            }

            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


    // --------- Updating Document details below start -------------


    public void updateBookDetails(Connection connection, Integer documentId, Object[] bookDetails) {

        String sql = "UPDATE Books SET title = ?, authors = ?, isbn = ?, publisher = ?, edition = ?, year = ?, num_pages = ?, total_copies = ? WHERE document_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            // Set the parameters for the PreparedStatement from the bookDetails array
            stmt.setString(1, (String) bookDetails[0]);  // title
            stmt.setString(2, (String) bookDetails[1]);  // authors
            stmt.setString(3, (String) bookDetails[2]);  // isbn
            stmt.setString(4, (String) bookDetails[3]);  // publisher
            stmt.setInt(5, (Integer) bookDetails[4]);    // edition
            stmt.setInt(6, (Integer) bookDetails[5]);    // year
            stmt.setInt(7, (Integer) bookDetails[6]);    // num_pages
            stmt.setInt(8, (Integer) bookDetails[7]);    // total_copies
            stmt.setInt(9, documentId);                  // document_id

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Book details updated successfully.");
            } else {
                System.out.println("No book found with document ID: " + documentId + ". No details were updated.");
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
        }
    }



    public void updateMagazineDetails(Connection connection, Integer documentId, Object[] magazineDetails) {
        String sql = "UPDATE Magazines SET magazine_name = ?, isbn = ?, publisher = ?, year = ?, month = ?, total_copies = ? WHERE document_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, (String) magazineDetails[0]);
            stmt.setString(2, (String) magazineDetails[1]);
            stmt.setString(3, (String) magazineDetails[2]);
            stmt.setInt(4, (Integer) magazineDetails[3]);
            stmt.setInt(5, (Integer) magazineDetails[4]);
            stmt.setInt(6, (Integer) magazineDetails[5]);
            stmt.setInt(7, documentId);


            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Magazine details updated successfully.");
            } else {
                System.out.println("No magazine found with document ID: " + documentId + ". No details were updated.");
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void updateJournalDetails(Connection connection, Integer documentId, Object[] journalDetails){

        String sql = "UPDATE Journals SET journal_name = ?, publisher = ?, total_copies = ? WHERE document_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, (String) journalDetails[0]);
            stmt.setString(2, (String) journalDetails[1]);
            stmt.setInt(3, (Integer) journalDetails[2]);
            stmt.setInt(4, documentId);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Journal details updated successfully.");
            } else {
                System.out.println("No journal found with document ID: " + documentId + ". No details were updated.");
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
        }

    }

    public void updateArticleDetails(Connection connection, Integer articleId, Object[] articleDetails){


        String sql = "UPDATE Articles SET issue_number = ?, volume_number = ?, year = ?, page_numbers = ? WHERE article_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, (Integer) articleDetails[0]);
            stmt.setInt(2, (Integer) articleDetails[1]);
            stmt.setInt(3, (Integer) articleDetails[2]);
            stmt.setString(4, (String) articleDetails[3]);
            stmt.setInt(5, articleId);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Article details updated successfully.");
            } else {
                System.out.println("No article found with article ID: " + articleId + ". No details were updated.");
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
        }

    }

    // --------- Updating Document details below end -------------


    // -----------returniing doument details below for each book, magazine and journal-------


    public static Object[] returnArticleDetails(Connection connection, Integer articleId) {

        String sql = "SELECT issue_number, volume_number, year, page_numbers " +
                "FROM Articles WHERE article_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setInt(1, articleId);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {

                    int issueNumber = rs.getInt("issue_number");
                    int volumeNumber = rs.getInt("volume_number");
                    int year = rs.getInt("year");
                    String pageNumbers = rs.getString("page_numbers");

                    Object[] articleDetails = {
                            issueNumber, volumeNumber, year, pageNumbers
                    };

                    return articleDetails;
                } else {
                    System.out.println("No article found with article ID: " + articleId);
                    return null;
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    public static Object[] returnJournalDetails(Connection connection, Integer documentId) {

        String sql = "SELECT journal_name, publisher, total_copies, total_copies_lended " +
                "FROM Journals WHERE document_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, documentId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String journalName = rs.getString("journal_name");
                    String publisher = rs.getString("publisher");
                    int totalCopies = rs.getInt("total_copies");
                    int totalCopiesLended = rs.getInt("total_copies_lended");

                    Object[] journalDetails = {
                            journalName, publisher, totalCopies, totalCopiesLended
                    };

                    return journalDetails;
                } else {
                    System.out.println("No journal found with document ID: " + documentId);
                    return null;
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    public static Object[] returnMagazineDetails(Connection connection, Integer documentId) {


        String sql = "SELECT magazine_name, isbn, publisher, year, month, total_copies, total_copies_lended " +
                "FROM Magazines WHERE document_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setInt(1, documentId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {

                    String magazineName = rs.getString("magazine_name");
                    String isbn = rs.getString("isbn");
                    String publisher = rs.getString("publisher");
                    int year = rs.getInt("year");
                    int month = rs.getInt("month");
                    int totalCopies = rs.getInt("total_copies");
                    int totalCopiesLended = rs.getInt("total_copies_lended");

                    Object[] magazineDetails = {
                            magazineName, isbn, publisher, year, month, totalCopies, totalCopiesLended
                    };

                    return magazineDetails;
                } else {
                    System.out.println("No magazine found with document ID: " + documentId);
                    return null;
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

    }

    public static Object[] returnBookDetails(Connection connection, Integer documentId) {

        String sql = "SELECT title, authors, isbn, publisher, edition, year, num_pages, total_copies, total_copies_lended " +
                "FROM Books WHERE document_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setInt(1, documentId);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {

                    String title = rs.getString("title");
                    String authors = rs.getString("authors");
                    String isbn = rs.getString("isbn");
                    String publisher = rs.getString("publisher");
                    int edition = rs.getInt("edition");
                    int year = rs.getInt("year");
                    int numPages = rs.getInt("num_pages");
                    int totalCopies = rs.getInt("total_copies");
                    int totalCopiesLended = rs.getInt("total_copies_lended");

                    Object[] bookDetails = {
                            title, authors, isbn, publisher, edition, year, numPages, totalCopies, totalCopiesLended
                    };

                    return bookDetails;
                } else {

                    System.out.println("No book found with document ID: " + documentId);
                    return null;
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // -----------returniing doument details end-----------



    // ----------- Grabbing the documents using maps start ------------

    // NEED TO WORK ON THIS WHEN I GET BACK
    Map<String, Integer> getArticles(Connection connection, Integer journalId) {

        Map<String, Integer> articlesMap = new HashMap<>();

        String sql = "SELECT article_id, article_title FROM Articles WHERE journal_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, journalId);

            // Execute the query
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int articleId = rs.getInt("article_id");
                    String articleTitle = rs.getString("article_title");
                    articlesMap.put(articleTitle, articleId);
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            e.printStackTrace();
        }

        return articlesMap;

    }


    Map<String, Integer> getJournals(Connection connection) {

        Map<String, Integer> journalsMap = new HashMap<>();

        String sql = "SELECT document_id, journal_name FROM Journals";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int documentId = rs.getInt("document_id");
                String journalName = rs.getString("journal_name");

                if (!journalsMap.containsKey(journalName)) {
                    journalsMap.put(journalName, documentId);
                } else {
                    journalsMap.put(journalName + " (ID: " + documentId + ")", documentId);
                }
            }
        } catch (SQLException e) {
            System.out.println("SQL error occurred while fetching journals:");
            e.printStackTrace();
        }

        return journalsMap;
    }


    Map<String, Integer> getMagazines(Connection connection) {

        Map<String, Integer> magazinesMap = new HashMap<>();

        String sql = "SELECT document_id, magazine_name FROM Magazines";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int documentId = rs.getInt("document_id");
                String magazineName = rs.getString("magazine_name");

                if (!magazinesMap.containsKey(magazineName)) {
                    magazinesMap.put(magazineName, documentId);
                } else {
                    magazinesMap.put(magazineName + " (ID: " + documentId + ")", documentId);
                }
            }
        } catch (SQLException e) {
            System.out.println("SQL error occurred while fetching magazines:");
            e.printStackTrace();
        }

        return magazinesMap;
    }



    // Grabbing all the books and storing it in map
    // Map keys will be book titles and their values will be the document "ID"
    Map<String, Integer> getBooks(Connection connection){

        Map<String, Integer> booksMap = new HashMap<>();

        String sql = "SELECT b.document_id, b.title FROM Books b JOIN Documents d ON b.document_id = d.document_id";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int documentId = rs.getInt("document_id");
                String title = rs.getString("title");

                if (!booksMap.containsKey(title)) {
                    booksMap.put(title, documentId);
                } else {
                    booksMap.put(title + " (ID: " + documentId + ")", documentId);
                }
            }
        } catch (SQLException e) {
            System.out.println("SQL error occurred while fetching books:");
            e.printStackTrace();
        }

        return booksMap;

    }

    // ----------- Grabbing the documents using maps start ------------


    void deleteADocument(Connection connection, Integer documentId){
        String sql = "DELETE FROM Documents WHERE document_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, documentId);
            pstmt.executeUpdate();
            System.out.println("Document deleted successfully.");
        }catch (SQLException e) {
            System.out.println("Error occurred during document deletion:");
            e.printStackTrace();
        }
    }

    void deleteAnArticle(Connection connection, Integer articleId){
        String sql = "DELETE FROM Articles WHERE article_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, articleId);
            pstmt.executeUpdate();
            System.out.println("Article deleted successfully.");
        }catch (SQLException e) {
            System.out.println("Error occurred during document deletion:");
            e.printStackTrace();
        }
    }

    void addArticles(Connection connection, Integer journalId, String articleTitle, String issueNumber,
                        String volumeNumber, String yearField, String pageNumbersField){
        String sql = "INSERT INTO Articles (journal_id, article_title, issue_number, volume_number, year, page_numbers) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, journalId);
            pstmt.setString(2, articleTitle);
            pstmt.setInt(3, Integer.parseInt(issueNumber));  // Convert issueNumber to integer
            pstmt.setInt(4, Integer.parseInt(volumeNumber));  // Convert volumeNumber to integer
            pstmt.setInt(5, Integer.parseInt(yearField));     // Convert yearField to integer
            pstmt.setString(6, pageNumbersField);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Article inserted successfully.");
            } else {
                System.out.println("Failed to insert the article.");
            }
        } catch (SQLException e) {
            System.out.println("SQL error occurred:");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Error converting string to integer. Check issue number, volume number, and year fields:");
            e.printStackTrace();
        }
    }

    void uploadJournal(Connection connection, Integer documentId, String journalName, String publisher,
                        String totalCopies){

        String sql = "INSERT INTO Journals (document_id, journal_name, publisher, total_copies) VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Set the parameters for the PreparedStatement
            pstmt.setInt(1, documentId);
            pstmt.setString(2, journalName);
            pstmt.setString(3, publisher);
            pstmt.setInt(4, Integer.parseInt(totalCopies));  // Convert totalCopies to integer

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Journal inserted successfully.");
            } else {
                System.out.println("No journal was inserted.");
            }
        } catch (SQLException e) {
            System.out.println("SQL error occurred during the insert operation:");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Error converting totalCopies to integer:");
            e.printStackTrace();
        }

    }


    //---------- Fix below by passing connection instead of building new ones and opening database over and over


    void uploadMagazine(Integer documentId, String magazineName, String isbn, String publisher,
                        String year, String month, String totalCopies){

        Connection connection = null;
        PreparedStatement pstmt = null;

        try {
            Class.forName("org.postgresql.Driver");

            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "INSERT INTO Magazines (document_id, magazine_name, isbn, publisher, year, month, total_copies) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";

                pstmt = connection.prepareStatement(sql);

                pstmt.setInt(1, documentId);
                pstmt.setString(2, magazineName);
                pstmt.setString(3, isbn);
                pstmt.setString(4, publisher);
                pstmt.setInt(5, Integer.parseInt(year));
                pstmt.setInt(6, Integer.parseInt(month));
                pstmt.setInt(7, Integer.parseInt(totalCopies));

                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("Magazine inserted successfully.");
                } else {
                    System.out.println("No magazine was inserted.");
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC Driver is not found. Include it in your library path.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("SQL error occurred:");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Error converting numeric fields:");
            e.printStackTrace();
        } finally {

            try {
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                System.out.println("Error closing connections:");
                ex.printStackTrace();
            }
        }

    }

    void uploadBook(int documentId, String title, String authors, String isbn, String publisher,
            String edition, String year, String numPages, String totalCopies){

        Connection connection = null;
        PreparedStatement pstmt = null;

        try {

            Class.forName("org.postgresql.Driver");

            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "INSERT INTO Books (document_id, title, authors, isbn, publisher, edition, year, num_pages, total_copies) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";


                pstmt = connection.prepareStatement(sql);

                pstmt.setInt(1, documentId);
                pstmt.setString(2, title);
                pstmt.setString(3, authors);
                pstmt.setString(4, isbn);
                pstmt.setString(5, publisher);
                pstmt.setInt(6, Integer.parseInt(edition));
                pstmt.setInt(7, Integer.parseInt(year));
                pstmt.setInt(8, Integer.parseInt(numPages));
                pstmt.setInt(9, Integer.parseInt(totalCopies));

                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("Book inserted successfully.");
                } else {
                    System.out.println("No book was inserted.");
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC Driver is not found. Include it in your library path.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("SQL error occurred:");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Error converting numeric fields:");
            e.printStackTrace();
        } finally {
            try {
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                System.out.println("Error closing connections:");
                ex.printStackTrace();
            }
        }

    }

    public Integer enterDocumentAndGetDocumentId(String documentType){

        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            Class.forName("org.postgresql.Driver");

            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            String sql = "INSERT INTO Documents (document_type) VALUES (?) RETURNING document_id";

            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, documentType);

            rs = pstmt.executeQuery();

            if (rs.next()) {
                int documentId = rs.getInt("document_id");
                System.out.println("Document inserted with ID: " + documentId);
                return documentId;
            } else {
                System.out.println("No document ID was generated.");
                return null;
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database access error:");
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException e) {
                System.out.println("Error closing resources:");
                e.printStackTrace();
            }
        }
        return null;

    }


    public void updateClientsCard(String oldCardNumber, String newCardNumber, int addressId, String clientEmail) {
        Connection connection = null;
        PreparedStatement pstmt = null;

        try {
            Class.forName("org.postgresql.Driver");

            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                if (oldCardNumber.equals(newCardNumber)) {
                    System.out.println("Same card number");
                    String sql = "UPDATE CreditCards SET payment_address_id = ? WHERE card_number = ?";
                    pstmt = connection.prepareStatement(sql);
                    pstmt.setInt(1, addressId);
                    pstmt.setString(2, oldCardNumber);
                } else {
                    System.out.println("Card not same");
                    String sql = "UPDATE CreditCards SET card_number = ?, payment_address_id = ? WHERE card_number = ?";
                    pstmt = connection.prepareStatement(sql);
                    pstmt.setString(1, newCardNumber);
                    pstmt.setInt(2, addressId);
                    pstmt.setString(3, oldCardNumber);
                }

                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("Credit card updated successfully.");
                } else {
                    System.out.println("Update failed: No rows affected.");
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database connection or SQL operation failed.");
            e.printStackTrace();
        } finally {
            try {
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                System.out.println("Failed to close database resources.");
                ex.printStackTrace();
            }
        }
    }

    //--------------------------------------------------------

    public void updateAddress(int addressId, String newAddress) {
        String url = "jdbc:postgresql://localhost:5432/Library";
        String user = "postgres";
        String password = "35Characters+";

        String sql = "UPDATE Addresses SET address = ? WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newAddress);
            pstmt.setInt(2, addressId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Address updated successfully.");
            } else {
                System.out.println("Update failed: Address not found or no changes made.");
            }
        } catch (SQLException e) {
            System.out.println("Error updating address: " + e.getMessage());
        }
    }

    //--------------------------------------------------------

    public void updateClientName(String email, String newName) {
        Connection connection = null;
        PreparedStatement pstmt = null;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "UPDATE Clients SET name = ? WHERE email = ?";
                pstmt = connection.prepareStatement(sql);

                pstmt.setString(1, newName);
                pstmt.setString(2, email);

                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("Client's name updated successfully.");
                } else {
                    System.out.println("Update failed: Client not found or no changes made.");
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database connection or SQL operation failed.");
            e.printStackTrace();
        } finally {
            try {
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                System.out.println("Failed to close database resources.");
                ex.printStackTrace();
            }
        }
    }

    //--------------------------------------------------------

    public Map<String, Integer> getClientCreditCards(String email) {
        Map<String, Integer> cards = new HashMap<>();
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "SELECT card_number, payment_address_id FROM CreditCards WHERE client_email = ?";
                pstmt = connection.prepareStatement(sql);
                pstmt.setString(1, email);

                rs = pstmt.executeQuery();

                while (rs.next()) {
                    String cardNumber = rs.getString("card_number");
                    int paymentAddressId = rs.getInt("payment_address_id");
                    cards.put(cardNumber, paymentAddressId);
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database connection or SQL operation failed.");
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        return cards;
    }

    //--------------------------------------------------------


    public void deleteClientByEmail(String email) {
        Connection connection = null;
        PreparedStatement pstmt = null;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "DELETE FROM Clients WHERE email = ?";
                pstmt = connection.prepareStatement(sql);
                pstmt.setString(1, email);

                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("Client deleted successfully.");
                } else {
                    System.out.println("No client found with the specified email.");
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database connection or SQL operation failed.");
            e.printStackTrace();
        } finally {
            try {
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    //--------------------------------------------------------

    // This function will return a map with keys as names and value as emails
    // So we can display it to user on which to pick when deleting or updating
    public Map<String, String> getClientInformation() {
        Map<String, String> clientInfo = new HashMap<>();
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "SELECT name, email FROM Clients";
                pstmt = connection.prepareStatement(sql);
                rs = pstmt.executeQuery();

                while (rs.next()) {
                    String name = rs.getString("name");
                    String email = rs.getString("email");
                    clientInfo.put(name, email); // Map client name to email
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database connection or SQL operation failed.");
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        return clientInfo;
    }

    //--------------------------------------------------------



    // This function adds the credit card associated with the client email
    void addCreditCard(String email, String creditNumber, int addressId) {
        Connection connection = null;
        PreparedStatement pstmt = null;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "INSERT INTO CreditCards (card_number, client_email, payment_address_id) VALUES (?, ?, ?)";
                pstmt = connection.prepareStatement(sql);

                pstmt.setString(1, creditNumber);
                pstmt.setString(2, email);
                pstmt.setInt(3, addressId);

                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("Credit card information added successfully.");
                } else {
                    System.out.println("No rows affected.");
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database connection or SQL operation failed.");
            e.printStackTrace();
        } finally {
            try {
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    //--------------------------------------------------------


    public Map<String, Integer> getClientAddresses(String email) {
        Map<String, Integer> addressMap = new HashMap<>();
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "SELECT id, address FROM Addresses WHERE client_email = ?";
                pstmt = connection.prepareStatement(sql);
                pstmt.setString(1, email);

                rs = pstmt.executeQuery();

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String address = rs.getString("address");
                    addressMap.put(address, id);
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database connection or SQL operation failed.");
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }

        return addressMap;
    }


    //--------------------------------------------------------

    void addClientAddresses(String email, ObservableList<TextField> addressFields) {
        Connection connection = null;
        PreparedStatement pstmt = null;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "INSERT INTO Addresses (client_email, address) VALUES (?, ?)";

                pstmt = connection.prepareStatement(sql);

                for (TextField addressField : addressFields) {
                    String address = addressField.getText();
                    if (address != null && !address.isEmpty()) {
                        pstmt.setString(1, email);
                        pstmt.setString(2, address);
                        pstmt.executeUpdate();
                    }
                }

                System.out.println("All addresses added successfully.");
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database connection or SQL operation failed.");
            e.printStackTrace();
        } finally {
            try {
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    //--------------------------------------------------------


    void addClientInformation(String name, String email, String password) {
        Connection connection = null;
        PreparedStatement pstmt = null;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "INSERT INTO Clients (email, name, password) VALUES (?, ?, ?)";
                pstmt = connection.prepareStatement(sql);

                pstmt.setString(1, email);
                pstmt.setString(2, name);
                pstmt.setString(3, password);

                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    System.out.println("Client information added successfully.");
                } else {
                    System.out.println("No rows affected.");
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Database connection or SQL operation failed.");
            e.printStackTrace();
        } finally {
            try {
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    //--------------------------------------------------------


    boolean verifyLibrarianLoginAttempt(String emailAddress, String password) {
        Connection connection = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String query = "SELECT password FROM Librarians WHERE email = ?";
                stmt = connection.prepareStatement(query);
                stmt.setString(1, emailAddress);
                rs = stmt.executeQuery();

                if (rs.next()) {
                    String retrievedPassword = rs.getString("password");
                    if (retrievedPassword != null && retrievedPassword.equals(password)) {
                        return true;
                    }
                }
            } else {
                System.out.println("Connection Failed");
            }
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Error occurred: " + e.getMessage());
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (connection != null) connection.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
        return false;
    }

    //--------------------------------------------------------


    void registerLibrarian(String ssn, String name, String email, BigDecimal salary, String password) {
        Connection connection = null;
        PreparedStatement pstmt = null;

        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");

                String sql = "INSERT INTO Librarians (ssn, name, email, salary, password) VALUES (?, ?, ?, ?, ?)";

                pstmt = connection.prepareStatement(sql);
                pstmt.setString(1, ssn);
                pstmt.setString(2, name);
                pstmt.setString(3, email);
                pstmt.setBigDecimal(4, salary);
                pstmt.setString(5, password);

                int rowsAffected = pstmt.executeUpdate();
                System.out.println("Rows affected: " + rowsAffected);

            } else {
                System.out.println("Connection Failed");
            }
        } catch (Exception e) {
            System.out.println("Error during database operation");
            System.out.println(e);
        } finally {
            try {
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (SQLException se) {
                se.printStackTrace();
            }
        }
    }

    //--------------------------------------------------------


    void connectDatabase() {

        Connection connection = null;

        try {

            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");
            } else {
                System.out.println("Connection Failed");
            }


        } catch (Exception e) {
            System.out.println("Loading Failed");
            System.out.println(e);
        }
    }



    //----------------------------------

    Connection establishConnectionToDatabase(){

        Connection connection = null;

        try {


            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Library", "postgres", "35Characters+");

            if (connection != null) {
                System.out.println("Connection Successful");
            } else {
                System.out.println("Connection Failed");
            }


        } catch (Exception e) {
            System.out.println("Loading Failed");
            System.out.println(e);
        }

        return connection;

    }

    }
