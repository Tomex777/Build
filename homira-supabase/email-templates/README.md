# Homira authentication email templates

These templates intentionally use `{{ .Token }}` rather than `{{ .ConfirmationURL }}`.

- `confirm-signup.html`: first-time email sign-up
- `magic-link-otp.html`: returning email OTP sign-in

The current Supabase client already verifies the emailed code with `verifyEmailOtp`, so no Android deep-link flow is required.

The mascot image is loaded from the Homira repository over HTTPS. The longer-term dynamic version can move sending to Supabase's Send Email Hook so greetings can rotate per request and returning users can be addressed by profile name.
