package com.example.stt;

import android.content.Context;
import android.util.Log;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.InsertTextRequest;
import com.google.api.services.docs.v1.model.Location;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.DeleteContentRangeRequest;
import com.google.api.services.docs.v1.model.Range;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class GoogleApiManager {

    private static final String TAG = "GoogleApiManager";
    private Docs docsService;
    private Drive driveService;

    public GoogleApiManager(Context context) {
        try {
            InputStream credentialsStream = context.getAssets().open("credentials.json");
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(Arrays.asList(
                            "https://www.googleapis.com/auth/documents",
                            "https://www.googleapis.com/auth/drive"));
            GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            docsService = new Docs.Builder(AndroidHttp.newCompatibleTransport(), jsonFactory,
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Hall of Brands STT")
                    .build();
            driveService = new Drive.Builder(AndroidHttp.newCompatibleTransport(), jsonFactory,
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Hall of Brands STT")
                    .build();
            Log.d(TAG, "Google API services initialized successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Google API services", e);
        }
    }

    public String createDocument(String title, String content) {
        try {
            Document doc = new Document();
            doc.setTitle(title);
            Document createdDoc = docsService.documents().create(doc).execute();
            String docId = createdDoc.getDocumentId();
            Log.d(TAG, "Document created with ID: " + docId);
            if (!content.trim().isEmpty()) {
                setDocumentContent(docId, content);
            }
            shareFileWithEmail(docId, "hallofbrands.greece@gmail.com");
            return docId;
        } catch (Exception e) {
            Log.e(TAG, "Error creating document", e);
            return null;
        }
    }

    // This method replaces the document's content.
    public void setDocumentContent(String docId, String newContent) {
        if(newContent == null) return;
        try {
            Document doc = docsService.documents().get(docId).execute();
            int endIndex = 1;
            List<com.google.api.services.docs.v1.model.StructuralElement> contentList = doc.getBody().getContent();
            if (contentList != null && !contentList.isEmpty() &&
                    contentList.get(contentList.size() - 1).getEndIndex() != null) {
                endIndex = contentList.get(contentList.size() - 1).getEndIndex();
            }
            List<Request> requests = new ArrayList<>();
            // Delete existing content (from index 1 to endIndex - 1).
            // Only add deletion if the range length is greater than zero.
            if(endIndex > 2) {  // Changed check: endIndex must be greater than 2.
                Request deleteRequest = new Request().setDeleteContentRange(
                        new DeleteContentRangeRequest()
                                .setRange(new Range()
                                        .setStartIndex(1)
                                        .setEndIndex(endIndex - 1))   // leaves the final newline intact.
                );
                requests.add(deleteRequest);
            }
            // Insert new content at index 1.
            Request insertRequest = new Request().setInsertText(
                    new InsertTextRequest()
                            .setText(newContent)
                            .setLocation(new Location().setIndex(1))
            );
            requests.add(insertRequest);
            BatchUpdateDocumentRequest body = new BatchUpdateDocumentRequest().setRequests(requests);
            docsService.documents().batchUpdate(docId, body).execute();
            Log.d(TAG, "Document content replaced successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error setting document content", e);
        }
    }

    public String getDocumentContent(String docId) {
        try {
            Document doc = docsService.documents().get(docId).execute();
            StringBuilder sb = new StringBuilder();
            List<com.google.api.services.docs.v1.model.StructuralElement> contentList = doc.getBody().getContent();
            if (contentList != null) {
                for (com.google.api.services.docs.v1.model.StructuralElement element : contentList) {
                    if (element.getParagraph() != null && element.getParagraph().getElements() != null) {
                        for (com.google.api.services.docs.v1.model.ParagraphElement pe : element.getParagraph().getElements()) {
                            if (pe.getTextRun() != null && pe.getTextRun().getContent() != null) {
                                sb.append(pe.getTextRun().getContent());
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "Fetched document content: " + sb.toString());
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error fetching document content", e);
            return "";
        }
    }

    public List<Pair> listDocuments() {
        List<Pair> list = new ArrayList<>();
        try {
            String query = "mimeType='application/vnd.google-apps.document'";
            Log.d(TAG, "Listing documents with query: " + query);
            FileList fileList = driveService.files().list()
                    .setQ(query)
                    .setFields("files(id, name)")
                    .execute();
            if (fileList != null && fileList.getFiles() != null) {
                for (File file : fileList.getFiles()) {
                    Log.d(TAG, "Found document: " + file.getName() + " (ID: " + file.getId() + ")");
                    list.add(new Pair(file.getId(), file.getName()));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing documents", e);
        }
        return list;
    }

    public void deleteDocument(String docId) {
        try {
            driveService.files().delete(docId).execute();
            Log.d(TAG, "Deleted document with ID: " + docId);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting document", e);
        }
    }

    public static class Pair {
        public String id;
        public String name;
        public Pair(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private void shareFileWithEmail(String fileId, String email) {
        try {
            Permission permission = new Permission();
            permission.setType("user");
            permission.setRole("writer");
            permission.setEmailAddress(email);
            driveService.permissions().create(fileId, permission)
                    .setFields("id")
                    .setSendNotificationEmail(false)
                    .execute();
            Log.d(TAG, "Shared file " + fileId + " with " + email);
        } catch (Exception e) {
            Log.e(TAG, "Error sharing file", e);
        }
    }
}
