# libphonenumber loads its bundled metadata by class-relative resource paths.
-keep class com.google.i18n.phonenumbers.** { *; }
-dontwarn javax.annotation.**
