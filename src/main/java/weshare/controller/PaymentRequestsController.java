package weshare.controller;

import io.javalin.http.Handler;
import weshare.model.*;
import weshare.persistence.ExpenseDAO;
import weshare.server.Routes;
import weshare.server.ServiceRegistry;
import weshare.server.WeShareServer;

import javax.money.MonetaryAmount;
import java.time.LocalDate;
import java.util.*;

public class PaymentRequestsController {

    public static final Handler paymentRequest_sent = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        Collection<Expense> expenses = expensesDAO.findExpensesForPerson(personLoggedIn);
        Collection<PaymentRequest> paymentRequests = new ArrayList<>();
        MonetaryAmount totalAmount = MoneyHelper.amountOf(0);
        for (Expense expense: expenses) {
            for (PaymentRequest paymentRequest: expense.listOfPaymentRequests()) {
                paymentRequests.add(paymentRequest);
                totalAmount = totalAmount.add(paymentRequest.getAmountToPay());
            }
        }
        System.out.println("total");
        System.out.println(totalAmount);
        Map<String, Object> viewModel = Map.of("paymentRequest", paymentRequests, "total", totalAmount );

        context.render("paymentrequests_sent.html", viewModel);
    };

    public static final Handler paymentRequest_received = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);

        Collection<PaymentRequest> paymentRequestsReceived = expensesDAO.findPaymentRequestsReceived(personLoggedIn);
        Collection<PaymentRequest> paymentRequests = new ArrayList<>();
        MonetaryAmount totalAmount = MoneyHelper.amountOf(0);
        for (PaymentRequest paymentRequest: paymentRequestsReceived) {
            paymentRequests.add(paymentRequest);
            totalAmount= totalAmount.add(paymentRequest.getAmountToPay());
        }

        Map<String, Object> viewModel = Map.of("paymentRequest", paymentRequests, "total", totalAmount );

        context.render("paymentrequests_received.html", viewModel);
    };

    public static Handler submitPaymentRequest = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);

        UUID expenseId = UUID.fromString(context.queryParam("expenseId"));

        Expense expense = expensesDAO.get(expenseId).get();

        long totalPaymentRequest = expense.getAmount().getNumber().intValueExact() -
                expense.totalAmountOfPaymentsRequested().getNumber().intValueExact();
        // MonetaryAmount totalPaymentRequest =  expense.totalAmountForPaymentsReceived();
        // System.out.println(totalPaymentRequest);
        HashMap<String, Object> viewModel = new HashMap<>();
        viewModel.put("expense", expense);
        viewModel.put("payments", expense.listOfPaymentRequests());
        viewModel.put("total_paymentrequests", totalPaymentRequest);

        context.render("paymentrequest.html", viewModel);
        // context.redirect("/paymentrequest?expenseId="+ expenseId);

        // context.redirect(Routes.PAYMENT_REQUEST);
    };

    public static Handler paymentAction = context -> {
        UUID request_id = UUID.fromString(context.formParamAsClass("request_id", String.class)
                .check(Objects::nonNull, "Payment Request ID is required")
                .get());

        ExpenseDAO expenseDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        Person personLoggedIn = WeShareServer.getPersonLoggedIn(context);
        Collection<PaymentRequest> paymentRequestsRecieved = expenseDAO.findPaymentRequestsReceived(personLoggedIn);

        for (PaymentRequest paymentRequest: paymentRequestsRecieved) {
            if(request_id.equals(paymentRequest.getId())){
                paymentRequest.pay(personLoggedIn, DateHelper.TODAY);
                expenseDAO.save(new Expense(personLoggedIn, paymentRequest.getDescription(), paymentRequest.getAmountToPay(), DateHelper.TODAY));
                break;
            }
        }

        context.redirect(Routes.PAYMENT_REQUESTS_RECEIVED);
    };


    public static Handler addPaymentRequest = context -> {
        ExpenseDAO expensesDAO = ServiceRegistry.lookup(ExpenseDAO.class);
        String expenseId = context.formParamAsClass("expense_id", String.class).get();
        String email = context.formParamAsClass("email", String.class).get();
        long amount = context.formParamAsClass("amount", long.class).get();
        String date = context.formParamAsClass("due_date", String.class).get();

        String [] dateArray = date.split("-");

        LocalDate localDate = LocalDate.of(Integer.parseInt(dateArray[0]), Integer.parseInt(dateArray[1]), Integer.parseInt(dateArray[2]));
        Person person = new Person(email);

        UUID id = UUID.fromString(expenseId);

        Expense expense = expensesDAO.get(id).get();

        expense.requestPayment(person, MoneyHelper.amountOf(amount), localDate);

        HashMap<String, Object> viewModel = new HashMap<>();
        viewModel.put("expense", expense);
        viewModel.put("payments", expense.listOfPaymentRequests());

        context.render("paymentrequest.html", viewModel);

    };

}
