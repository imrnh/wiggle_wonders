package org.ww.wigglew.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;
import org.ww.wigglew.entity.doctor.DoctorEntity;
import org.ww.wigglew.service.DoctorManagerService;

@RestController //will remove later. just testing jwt claim extraction.
@RequestMapping("/api/v1/doctor/")
public class DoctorController {

    @Autowired
    private DoctorManagerService doctorManagerService;

    private boolean validateAuthority(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails)
            return userDetails.getAuthorities().toString().equals("[ADMIN]");
        return false;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createDoctor(@RequestBody DoctorEntity doctorEntity) {
        if (!validateAuthority()) return ResponseEntity.status(403).body("Forbidden");
        return doctorManagerService.createDoctor(doctorEntity);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteDoctor(@PathVariable String id) {
        if (!validateAuthority()) return ResponseEntity.status(403).body("Forbidden");
        return doctorManagerService.deleteDoctor(id);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateDoctor(@PathVariable String id, @RequestBody DoctorEntity doctorEntity) {
        if (!validateAuthority()) return ResponseEntity.status(403).body("Forbidden");
        return doctorManagerService.updateDoctor(id, doctorEntity);
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<?> getDoctor(@PathVariable String id) {
        //if (!validateAuthority()) return ResponseEntity.status(403).body("Forbidden"); // anyone can get doctor including users.
        return doctorManagerService.getDoctor(id);
    }

    @GetMapping("/get-all/")
    public ResponseEntity<?> getAllDoctors(@RequestHeader("X-IP-Address") String ip) {
        return doctorManagerService.getAllDoctors(ip);
    }
}
