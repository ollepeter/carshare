package com.codecool.carshare.controller;

import com.codecool.carshare.model.LocationFilter;
import com.codecool.carshare.model.User;
import com.codecool.carshare.service.ReservationService;
import com.codecool.carshare.service.UserService;
import com.codecool.carshare.service.VehicleService;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import javax.websocket.server.PathParam;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

@Controller
public class RouteController {

    @Autowired
    private UserService userService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private LocationFilter locationFilter;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String index(Model model, HttpSession session,
                        @RequestParam(value = "type", required = false) String filter) {
        model.addAllAttributes(vehicleService.renderVehicles(filter));
        model.addAttribute("user", userService.getSessionUser(session));
        return "index";
    }

    @RequestMapping(value = "/vehicles/{id}", method = RequestMethod.GET)
    public String detailsPage(Model model, @PathVariable("id") String id, HttpSession session) {
        model.addAllAttributes(vehicleService.details(id, session));
        return "details";
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String loginPage(HttpSession session) {
        User user = userService.getSessionUser(session);
        if (user != null) return "redirect:/";

        return "login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String loginPage(Model model,
                            HttpSession session,
                            @RequestParam("username") String username,
                            @RequestParam("password") String password)
            throws InvalidKeySpecException, NoSuchAlgorithmException {
        model.addAllAttributes(userService.login(username, password, session));
        return "redirect:/";
    }

    @RequestMapping(value = "/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("user");
        return "redirect:/login";
    }

    @RequestMapping(value = "/register", method = RequestMethod.GET)
    public String registerPage() {
        return "register";
    }

    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public String registerUser(Model model, HttpSession session,
                               @RequestParam("username") String username,
                               @RequestParam("password") String password,
                               @RequestParam("email") String email,
                               @RequestParam("confirm-password") String confirmPassword)
            throws InvalidKeySpecException, NoSuchAlgorithmException {

        if (userService.register(username, password, confirmPassword, email, model)) {
            session.setAttribute("user", username);
            return "redirect:/";
        } else {
            return "/register";
        }
    }

    @RequestMapping(value = "/user/{id}", method = RequestMethod.GET)
    @Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public String renderProfilePage(Model model, HttpSession session, @PathParam("id") Integer id) {
        User user = userService.getSessionUser(session);
        if (user == null) return "redirect:/";
        model.addAttribute("user", user);
        model.addAttribute("uploadpage", true);
        return "userProfile";
    }

    @RequestMapping(value = "/upload", method = RequestMethod.GET)
    public String uploadVehiclePage(Model model, HttpSession session) {
        String username = (String) session.getAttribute("user");
        if (username != null) {
            User user = userService.getSessionUser(session);
            model.addAttribute("user", user);
        } else {
            return "redirect:/";
        }
        return "upload";
    }

    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public String uploadVehicle(Model model, HttpSession session,
                                @RequestParam("name") String name,
                                @RequestParam("year") String year,
                                @RequestParam("numofseats") String seats,
                                @RequestParam("type") String type,
                                @RequestParam("piclink") String piclink,
                                @RequestParam("startDate") String startDate,
                                @RequestParam("endDate") String endDate,
                                @RequestParam("location") String location) {
        model.addAllAttributes(vehicleService.uploadVehicle(name, year, seats, type, piclink, startDate, endDate, location, session));
        User user = userService.getSessionUser(session);
        model.addAttribute("user", user);

        model.addAttribute("profilePicture", user.getProfilePicture());
        return "redirect:" + "/user/" + user.getId();
    }

    @RequestMapping(value = "/{id}/upload-profile-pic", method = RequestMethod.POST)
    public String uploadProfilePicture(HttpSession session,
                                       @PathVariable("id") String id,
                                       @RequestParam("profilePicture") String profilePicture) {
        userService.uploadProfilePicture(userService.getSessionUser(session), profilePicture);
        return "redirect:/user/" + id;
    }

    @RequestMapping(value = "/vehicles/{id}/reservation", method = RequestMethod.POST)
    public String reserveVehicle(HttpSession session, Model model,
                                 @PathVariable("id") String vehicleId,
                                 @RequestParam("reservation_startdate") String resStartString,
                                 @RequestParam("reservation_enddate") String resEndString) {

        if (reservationService.reserveVehicle(model, session, vehicleId, resStartString, resEndString)) {
            return "redirect:/vehicles/" + vehicleId + "/reservation";
        }
        else {
            if (model.containsAttribute("error")) {
                String error = (String) model.asMap().get("error");
                if (error.equals("not_logged_in")) {
                    return "redirect:/login";
                }
                if (error.equals("invalid_date")) {
                    return "redirect:/vehicles/" + vehicleId;
                }
            }

            return "redirect:/";
        }
    }

    @RequestMapping(value = "/vehicles/{id}/reservation", method = RequestMethod.GET)
    public String billingInfoPage(HttpSession session, Model model,
                                  @PathVariable("id") String vehicleId) {
        if (session.getAttribute("reservation") != null) {
            model.addAttribute("user", userService.getSessionUser(session));
            model.addAttribute("vehicle", vehicleService.findVehicleById(Integer.valueOf(vehicleId)));
            model.addAttribute("reservation", session.getAttribute("reservation"));
            return "billing";
        }
        else {
            return "redirect:/vehicles/" + vehicleId;
        }
    }

    @RequestMapping(value = "/billingData", method = RequestMethod.POST)
    public String makeReservation(HttpSession session) {

        reservationService.makeReservation(session);

        return "redirect:/";
    }

    @RequestMapping(value = "/locationData", method = RequestMethod.GET)
    @ResponseBody
    public JSONObject json() {
        return vehicleService.jsonify(vehicleService.getAllLocation());

    }

    @RequestMapping(value = "/locations", method = RequestMethod.GET)
    public String map(Model model, HttpSession session) {
        User user = userService.getSessionUser(session);
        model.addAttribute("user", user);
        return "locations";
    }

    @RequestMapping(value = "/locations/{cityIndex}", method = RequestMethod.POST)
    public String getVehiclesByLocation(@RequestParam("index") String index,
                                        @RequestParam("cityName") String cityName) {
        locationFilter.setVehiclesByLocation(vehicleService.getAllVehiclesByLocation(cityName));
        locationFilter.setCity(cityName);
        return "vehiclesinlocation";
    }

    @RequestMapping(value = "/locations/{cityIndex}", method = RequestMethod.GET)
    public String getVehics(Model model, HttpSession session) {
        User user = userService.getSessionUser(session);
        model.addAttribute("user", user);
        model.addAttribute("vehicles", locationFilter.getVehiclesByLocation());
        model.addAttribute("location", locationFilter.getCity());
        return "vehiclesinlocation";
    }

}
