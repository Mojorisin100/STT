package com.example.stt;

import android.os.AsyncTask;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailSender {

    public interface EmailCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    // Update these with your credentials and server details
    private static final String SMTP_SERVER = "mail.hallofbrands.gr";
    private static final int SMTP_PORT = 587;
    private static final String SENDER_EMAIL = "operations@hallofbrands.gr";
    private static final String SENDER_PASSWORD = "Ct6%26gd5";
    private static final String FORWARD_EMAIL = "nikossiontis@siontisnsa.gr";

    public static void sendEmail(final String subject, final String body, final EmailCallback callback) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Properties props = new Properties();
                    props.put("mail.smtp.auth", "true");
                    props.put("mail.smtp.starttls.enable", "true");
                    props.put("mail.smtp.host", SMTP_SERVER);
                    props.put("mail.smtp.port", String.valueOf(SMTP_PORT));

                    Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                        }
                    });

                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress(SENDER_EMAIL));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(FORWARD_EMAIL));
                    message.setSubject(subject);
                    message.setText(body);

                    Transport.send(message);
                    if (callback != null) callback.onSuccess();
                } catch (Exception e) {
                    if (callback != null) callback.onFailure(e);
                }
            }
        });
    }
}
