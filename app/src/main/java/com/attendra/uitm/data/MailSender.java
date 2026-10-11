package com.attendra.uitm.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.attendra.uitm.BuildConfig;
import com.attendra.uitm.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.activation.DataHandler;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

/**
 * Sends the verification code email through Gmail's SMTP server with JavaMail
 * (CLAUDE.md 12.3): smtp.gmail.com, port 587, STARTTLS.
 *
 * Network calls are not allowed on the main (UI) thread on Android, so the
 * send runs on a background thread from an ExecutorService. The result is
 * passed back to the main thread, so callers can update the screen directly.
 *
 * Note for the report: this is a prototype design. The SMTP password is inside
 * the app (from local.properties via BuildConfig). In production a server
 * (e.g. Cloud Functions) would send the email instead.
 */
public class MailSender {

    private static final String TAG = "MailSender";
    private static final String HOST = "smtp.gmail.com";
    private static final String PORT = "587";
    private static final String TIMEOUT_MS = "15000";
    private static final String FROM_NAME = "Attendra UiTM";
    private static final String SUBJECT = "Your Attendra UiTM verification code";

    /** One background thread is enough: emails are sent one at a time. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context context;

    public MailSender(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Sends the code email in the background.
     *
     * @param role "student" or "lecturer", shown in the email text.
     * On failure the exception is logged and onError gets the message from 12.3.
     */
    public void sendVerificationCode(String toEmail, String name, String role, String code,
                                     Callback<Void> callback) {
        EXECUTOR.execute(() -> {
            try {
                if (BuildConfig.SMTP_USER.isEmpty() || BuildConfig.SMTP_PASS.isEmpty()) {
                    throw new IllegalStateException("SMTP_USER / SMTP_PASS missing in local.properties");
                }
                send(buildMessage(toEmail, name, role, code));
                MAIN.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                Log.e(TAG, "Could not send the verification email", e);
                MAIN.post(() -> callback.onError(context.getString(R.string.code_send_failed)));
            }
        });
    }

    private Session session() {
        Properties props = new Properties();
        props.put("mail.smtp.host", HOST);
        props.put("mail.smtp.port", PORT);
        props.put("mail.smtp.auth", "true");
        // Port 587 starts as plain text and then upgrades to an encrypted
        // connection (STARTTLS). "required" refuses to send if that upgrade fails,
        // so the password is never sent unencrypted.
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        // Without timeouts a bad connection could block the thread for minutes.
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(BuildConfig.SMTP_USER, BuildConfig.SMTP_PASS);
            }
        });
    }

    /**
     * MIME layout of the email:
     *
     *   multipart/alternative
     *   ├── text/plain          (for mail apps that don't show HTML)
     *   └── multipart/related   (the HTML version and what it needs)
     *       ├── text/html       (res/raw/verification_email.html)
     *       └── image/png       (the gold logo: Content-ID <logo>, used as cid:logo)
     *
     * The logo sits INSIDE the HTML's related part, has Content-Disposition
     * INLINE and no filename. That tells mail apps it is part of the page, so
     * Gmail shows it in the email and not as an attachment chip (CLAUDE.md 12.6 C).
     */
    private MimeMessage buildMessage(String toEmail, String name, String role, String code)
            throws MessagingException, IOException {
        String minutes = String.valueOf(BuildConfig.CODE_TTL_MINUTES);
        String year = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));

        String html = readRaw(R.raw.verification_email)
                .replace("{{NAME}}", escapeHtml(name))
                .replace("{{CODE}}", code)
                .replace("{{MINUTES}}", minutes)
                .replace("{{ROLE}}", escapeHtml(role))
                .replace("{{EMAIL}}", escapeHtml(toEmail))
                .replace("{{YEAR}}", year);
        String text = context.getString(R.string.code_email_text, name, code, minutes, role,
                toEmail, year);

        // The HTML and its logo, bundled as multipart/related
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");
        MimeBodyPart logoPart = new MimeBodyPart();
        logoPart.setDataHandler(new DataHandler(
                new ByteArrayDataSource(readBytes(R.drawable.attendra_mark_gold), "image/png")));
        logoPart.setContentID("<logo>");
        logoPart.setDisposition(MimeBodyPart.INLINE); // no setFileName(): not an attachment
        MimeMultipart related = new MimeMultipart("related");
        related.addBodyPart(htmlPart);
        related.addBodyPart(logoPart);
        MimeBodyPart relatedWrapper = new MimeBodyPart();
        relatedWrapper.setContent(related);

        // Plain text first, then the HTML version: mail apps show the last one they can.
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(text, "UTF-8");
        MimeMultipart alternative = new MimeMultipart("alternative");
        alternative.addBodyPart(textPart);
        alternative.addBodyPart(relatedWrapper);

        // "Attendra UiTM" is the name inboxes show instead of the bare address (12.6 B).
        InternetAddress sender = new InternetAddress(BuildConfig.SMTP_USER, FROM_NAME, "UTF-8");
        MimeMessage message = new MimeMessage(session());
        message.setFrom(sender);
        message.setReplyTo(new InternetAddress[]{sender});
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
        message.setSubject(SUBJECT, "UTF-8");
        message.setContent(alternative);
        return message;
    }

    private void send(MimeMessage message) throws MessagingException {
        Transport.send(message);
    }

    private String readRaw(int resId) throws IOException {
        return new String(readBytes(resId), StandardCharsets.UTF_8);
    }

    private byte[] readBytes(int resId) throws IOException {
        try (InputStream in = context.getResources().openRawResource(resId)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    /** The name and email are typed by the user, so make them safe to put in HTML. */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
