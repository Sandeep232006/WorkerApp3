GIG WORKER EARNINGS TRACKER (web app on top of your Java project)

1. Put YOUR GigEconomySystem.java (the one with Job, Worker, Transaction,
   ReconciliationRecord) in this same folder. Do not change it.
2. Open a terminal in this folder and run:
      javac GigEconomySystem.java WebApp.java
      java WebApp
3. Open http://localhost:8080
   Worker logins: W1 to W100, password 23042006 for every account
   (or create a new worker account on the login page)

ADMIN: sign in with ID Sandeep and password 23042006 to see all workers and all discrepancies.

JOB DURATION: every job has a start date, an end date and hours per day.
Two jobs of the same worker can never share a day (a worker cannot work on two platforms on the same day).
