package tn.esprit.services;

import tn.esprit.entities.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class  AppointmentDoctorDirectory {
    private static final Map<String, List<DoctorProfile>> DOCTORS_BY_SPECIALTY = buildDoctorsBySpecialty();

    private AppointmentDoctorDirectory() {
    }

    public static List<String> getSpecialties() {
        return new ArrayList<>(DOCTORS_BY_SPECIALTY.keySet());
    }

    public static List<User> getDoctorsForSpecialty(String specialty) {
        List<DoctorProfile> profiles = DOCTORS_BY_SPECIALTY.getOrDefault(specialty, DOCTORS_BY_SPECIALTY.get("General Care"));
        List<User> doctors = new ArrayList<>();
        for (DoctorProfile profile : profiles) {
            User doctor = new User();
            doctor.setFullName(profile.name());
            doctor.setFirstName(profile.name().replace("Dr. ", "").split(" ")[0]);
            doctor.setLastName(profile.name().replace("Dr. ", "").substring(profile.name().replace("Dr. ", "").indexOf(' ') + 1));
            doctor.setEmail(profile.email());
            doctor.setRole(UserService.ROLE_DOCTOR);
            doctor.setSpeciality(specialty);
            doctors.add(doctor);
        }
        return doctors;
    }

    private static Map<String, List<DoctorProfile>> buildDoctorsBySpecialty() {
        Map<String, List<DoctorProfile>> doctors = new LinkedHashMap<>();
        doctors.put("General Care", List.of(
                doctor("Dr. Nesrine Ayari", "nesrine.ayari"),
                doctor("Dr. Firas Ben Hmida", "firas.benhmida"),
                doctor("Dr. Amal Saidi", "amal.saidi"),
                doctor("Dr. Marwen Gharbi", "marwen.gharbi")
        ));
        doctors.put("Cardiology", List.of(
                doctor("Dr. Walid Trabelsi", "walid.trabelsi"),
                doctor("Dr. Mouna Khelifi", "mouna.khelifi"),
                doctor("Dr. Hichem Ben Ammar", "hichem.benammar"),
                doctor("Dr. Rym Haddad", "rym.haddad")
        ));
        doctors.put("Pulmonology", List.of(
                doctor("Dr. Leila Jaziri", "leila.jaziri"),
                doctor("Dr. Karim Chatti", "karim.chatti"),
                doctor("Dr. Salma Ben Romdhane", "salma.benromdhane"),
                doctor("Dr. Mourad Dhouib", "mourad.dhouib")
        ));
        doctors.put("Neurology", List.of(
                doctor("Dr. Olfa Ben Othman", "olfa.benothman"),
                doctor("Dr. Kais Hamza", "kais.hamza"),
                doctor("Dr. Souhir Belhaj", "souhir.belhaj"),
                doctor("Dr. Sami Kammoun", "sami.kammoun")
        ));
        doctors.put("Orthopedics", List.of(
                doctor("Dr. Anis Bouaziz", "anis.bouaziz"),
                doctor("Dr. Seif Eddine Karray", "seif.karray"),
                doctor("Dr. Wafa Cherif", "wafa.cherif"),
                doctor("Dr. Slim Ben Salah", "slim.bensalah")
        ));
        doctors.put("Ophthalmology", List.of(
                doctor("Dr. Ines Ghannouchi", "ines.ghannouchi"),
                doctor("Dr. Nizar Toumi", "nizar.toumi"),
                doctor("Dr. Marwa Ben Naceur", "marwa.bennaceur"),
                doctor("Dr. Aymen Louati", "aymen.louati")
        ));
        doctors.put("Dermatology", List.of(
                doctor("Dr. Nour Baccouche", "nour.baccouche"),
                doctor("Dr. Mehdi Jaziri", "mehdi.jaziri"),
                doctor("Dr. Sarra Mzoughi", "sarra.mzoughi"),
                doctor("Dr. Lina Ferchichi", "lina.ferchichi")
        ));
        doctors.put("Pediatrics", List.of(
                doctor("Dr. Amel Masmoudi", "amel.masmoudi"),
                doctor("Dr. Tarek Bouslama", "tarek.bouslama"),
                doctor("Dr. Mariem Zribi", "mariem.zribi"),
                doctor("Dr. Oussama Chaker", "oussama.chaker")
        ));
        doctors.put("Emergency Specialist", List.of(
                doctor("Dr. Sami Trabelsi", "sami.trabelsi"),
                doctor("Dr. Hela Ben Ahmed", "hela.benahmed"),
                doctor("Dr. Yassine Mejri", "yassine.mejri"),
                doctor("Dr. Karim Chatti", "karim.chatti.urgent")
        ));
        return Collections.unmodifiableMap(doctors);
    }

    private static DoctorProfile doctor(String name, String emailPrefix) {
        return new DoctorProfile(name, emailPrefix + "@pinkshield.tn");
    }

    private record DoctorProfile(String name, String email) {
    }
}
