package com.codecool.carshare.controller;

import com.codecool.carshare.model.User;
import com.codecool.carshare.model.UserProfilePicture;
import com.codecool.carshare.model.Vehicle;
import com.codecool.carshare.model.VehicleType;
import com.codecool.carshare.model.email.ReservationMail;
import com.codecool.carshare.model.email.WelcomeMail;
import com.codecool.carshare.utility.DataManager;
import com.codecool.carshare.utility.SecurePassword;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.template.thymeleaf.ThymeleafTemplateEngine;

import javax.persistence.*;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class PageController {
    private static UserProfilePicture profilePictureLink;
    private static String userName;
    private static String emailAddress;

    public static String renderVehicles(Request req, Response res) {

        EntityManagerFactory emf = DataManager.getEntityManagerFactory();
        EntityManager em = emf.createEntityManager();

        HashMap<String, Object> params = new HashMap<>();
        String filterString = req.queryParams("type");
        VehicleType type = VehicleType.getTypeFromString(filterString);
        List results;
        if (type != null) {
            Query filterQuery = em.createNamedQuery("Vehicle.getByType", Vehicle.class).setParameter("type", type);
            results = filterQuery.getResultList();
        } else {
            results = em.createNamedQuery("Vehicle.getAll", Vehicle.class).getResultList();
        }

        String username = req.session().attribute("user");
        if (username != null) {
            User user = getUserByName(username);
            params.put("user", user);
        }
        params.put("types", Arrays.asList(VehicleType.values()));
        params.put("vehicles", results);

        em.close();

        return renderTemplate(params, "index");
    }


    public static String details(Request req, Response res) {
        EntityManagerFactory emf = DataManager.getEntityManagerFactory();
        EntityManager em = emf.createEntityManager();

        Map<String, Vehicle> params = new HashMap<>();
        int vehicleId = Integer.valueOf(req.params("id"));


        Vehicle resultVehicle = em.createNamedQuery("Vehicle.getById", Vehicle.class)
                .setParameter("vehicleId", vehicleId).getSingleResult();

        if (req.requestMethod().equalsIgnoreCase("POST")) {
            ReservationMail reservationMail = new ReservationMail();
            reservationMail.sendEmail(emailAddress, userName);
            res.redirect("/");
        }

        params.put("vehicle", resultVehicle);
        return renderTemplate(params, "details");

    }


    public static String register(Request req, Response res) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {

        Map<String, String> params = new HashMap<>();

        if (req.requestMethod().equalsIgnoreCase("POST")) {

            String username = req.queryParams("username").toLowerCase().trim();
            String email = req.queryParams("email");
            String password = req.queryParams("password");
            String confirmPassword = req.queryParams("confirm-password");

            // save data for sending further emails
            userName = username;
            emailAddress = email;

            if (username.equals("") || email.equals("") || password.equals("") || confirmPassword.equals("")) {
                System.out.println("One ore more field is empty");
                params.put("errorMessage", "All fields are required");
                params.put("username", username);
                params.put("email", email);

                return renderTemplate(params, "register");
            }

            // send welcome mail to registered e-mail address
            WelcomeMail welcomeMail = new WelcomeMail();
            welcomeMail.sendEmail(email, username);

            String passwordHash = SecurePassword.createHash(password);

            if (password.equals(confirmPassword)) {
                User user = new User(username, email, passwordHash);
                persist(user);
                return loginUser(req, res, username);
            } else {
                params.put("errorMessage", "Confirm password");
                params.put("username", username);
                params.put("email", email);
                params.put("focus", "password");
            }
        }

        return renderTemplate(params, "register");
    }

    public static String uploadVehicle(Request req, Response res) {
        HashMap<String, Object> params = new HashMap<>();
        String username = req.session().attribute("user");
        if (username != null) {
            User user = getUserByName(username);
            params.put("user", user);
        }
        params.put("profilePicture", getProfilePictureLink());

        if (req.requestMethod().equalsIgnoreCase("POST")) {
            String name = req.queryParams("name");
            String year = req.queryParams("year");
            String seats = req.queryParams("numofseats");
            String type = req.queryParams("type");
            String piclink = req.queryParams("piclink");
            String startDate = req.queryParams("startDate");
            String endDate = req.queryParams("endDate");

            VehicleType vehicleType = VehicleType.getTypeFromString(type);

            int yearInt = Integer.parseInt(year);
            int numOfSeats = Integer.parseInt(seats);
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            try {
                Date startDateF = df.parse(startDate);
                Date endDateF = df.parse(endDate);
                Vehicle vehicle = new Vehicle(name, yearInt, numOfSeats, vehicleType, piclink, startDateF, endDateF);

                // sets owner to uploaded car
                User owner = getUserByName(username);
                vehicle.setOwner(owner);

                persist(vehicle);
            } catch (ParseException e) {
                e.printStackTrace();
            }

            res.redirect("/");
        }

        return renderTemplate(params, "upload");
    }

    public static String login(Request req, Response res) throws InvalidKeySpecException, NoSuchAlgorithmException {
        if (userLoggedIn(req, res)) return "";

        Map<String, Object> params = new HashMap<>();
        if (req.requestMethod().equalsIgnoreCase("POST")) {
            String name = convertField(req.queryParams("username"));
            String password = req.queryParams("password");

            String storedPassword;

            if (password.equals("") || name.equals("")) {
                System.out.println("One ore more field is empty");
                params.put("errorMessage", "All fields are required");
                return renderTemplate(params, "login");
            } else {
                storedPassword = getPasswordByName(name);
            }

            if (storedPassword != null && SecurePassword.isPasswordValid(password, storedPassword)) {
                return loginUser(req, res, name);
            } else {
                params.put("errorMessage", "Invalid username or password");
            }

        }

        return renderTemplate(params, "login");
    }

    public static String owner(Request request, Response response) {
        EntityManagerFactory emf = DataManager.getEntityManagerFactory();
        EntityManager em = emf.createEntityManager();
        HashMap<String, Object> params = new HashMap<>();
        String username = request.session().attribute("user");
        User result = getUserByName(username);
        int userId = 0;
        if (result != null) {
            userId = result.getId();
        }
        if (request.requestMethod().equalsIgnoreCase("POST")) {
            String profilePicture = request.queryParams("profilePicture");
            UserProfilePicture userProfilePicture = new UserProfilePicture(profilePicture);
            userProfilePicture.setUser(result);
            persist(userProfilePicture);
        }
        try {
            UserProfilePicture profilePicture = em.createNamedQuery("getUsersProfPic", UserProfilePicture.class).setParameter("user_id", userId).getSingleResult();
            params.put("profilePicture", profilePicture);
            profilePictureLink = profilePicture;
        } catch (NoResultException e) {
            UserProfilePicture defaultPicture = new UserProfilePicture();
            defaultPicture.setProfilePicture("/default_pic.jpg");
            params.put("profilePicture", defaultPicture);
            profilePictureLink = defaultPicture;
        }
        params.put("user", result);
        return renderTemplate(params, "userProfile");
    }


    public static String logout(Request req, Response res) {
        System.out.println(req.session().attribute("user") + " logged out");
        req.session().removeAttribute("user");
        res.redirect("/login");
        return "";
    }

    private static String getPasswordByName(String name) {
        EntityManagerFactory emf = DataManager.getEntityManagerFactory();
        EntityManager em = emf.createEntityManager();
        try {
            String storedPassword = (String) em.createNamedQuery("User.getPasswordHash")
                    .setParameter("name", name)
                    .getSingleResult();
            em.close();
            return storedPassword;
        } catch (NoResultException e) {
            System.out.println("No such a user in db");
        }
        return null;
    }

    private static User getUserByName(String name) {
        EntityManager em = DataManager.getEntityManagerFactory().createEntityManager();
        try {

            User user = (User) em.createNamedQuery("User.getUserByName")
                    .setParameter("name", name)
                    .getSingleResult();
            em.close();
            return user;
        } catch (NoResultException e) {
            System.out.println("No such a user in db");
        }
        return null;
    }

    private static void persist(Object object) {
        EntityManagerFactory emf = DataManager.getEntityManagerFactory();
        EntityManager em = emf.createEntityManager();
        EntityTransaction transaction = em.getTransaction();

        transaction.begin();
        em.persist(object);
        transaction.commit();
        em.close();
    }

    private static String renderTemplate(Map model, String template) {
        return new ThymeleafTemplateEngine().render(new ModelAndView(model, template));
    }

    private static String convertField(String string) {
        return string.toLowerCase().trim().replaceAll("\\s+", "");
    }

    private static boolean userLoggedIn(Request req, Response res) {
        if (req.session().attribute("user") != null) {
            System.out.println(req.session().attribute("user") + " are already logged in");
            res.redirect("/");
            return true;
        }
        return false;
    }

    private static String loginUser(Request req, Response res, String name) {
        req.session().attribute("user", name);
        System.out.println(req.session().attribute("user") + " logged in");
        res.redirect("/");
        return "";
    }

    public static UserProfilePicture getProfilePictureLink() {
        return profilePictureLink;
    }
}