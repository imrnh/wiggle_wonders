package org.ww.wigglew.auth;

import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.ww.wigglew.auth.entity.PhoneNumberVerificationStatus;
import org.ww.wigglew.auth.entity.UserEntity;
import org.ww.wigglew.auth.models.LoginRequest;
import org.ww.wigglew.auth.models.PasswordResetRequest;
import org.ww.wigglew.auth.models.RegisterRequest;
import org.ww.wigglew.auth.entity.AccessRole;
import org.ww.wigglew.auth.phone_verify.SmsSenderService;
import org.ww.wigglew.config.jwt.JWTGeneratorService;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

@Service
@NoArgsConstructor
public class AuthenticationService {
    @Autowired private  UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JWTGeneratorService jwtGeneratorService;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private SmsSenderService smsService;

    /**
     * Save the user to database and then make a JWT token.
     * @param request: The request's JSON Body.
     * @return JWT Token.
     */
    public AuthenticationResponse register(RegisterRequest request){
        var user = UserEntity.builder()
                .fullName(request.getFullName())
                .phone("+88" + request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(AccessRole.USER)
                .verificationStatus(PhoneNumberVerificationStatus.UNVERIFIED)
                .build();

        userRepository.save(user);

        //generate an OTP. Push the username (phone number in this case) and OTP to the database along with timestamp of creation.
        sendOTP(user.getPhone());

        return AuthenticationResponse.builder().token(null)
                .fullName(user.getFullName())
                .verificationStatus(user.getVerificationStatus() == PhoneNumberVerificationStatus.VERIFIED)
                .build();
    }

    public AuthenticationResponse login(LoginRequest request){
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        "+88" + request.getPhone(),
                        request.getPassword()
                )
        );

        //find user and generate token for the user.
        try{
            var user = userRepository.findByPhone("+88" + request.getPhone()).orElseThrow();

            //check phone number verified or not.
            if(user.getVerificationStatus() == PhoneNumberVerificationStatus.UNVERIFIED){
                return AuthenticationResponse.builder().token(null).fullName(user.getFullName()).verificationStatus(false).build();
            }

            //Create JWT Token.
            var jwtToken = jwtGeneratorService.generateToken(user);

            return AuthenticationResponse.builder()
                    .token(jwtToken)
                    .fullName(user.getFullName())
                    .verificationStatus(true).requestSuccess(true).requestMessage("Logged In").build();
        }
        catch (Exception e){
            return  AuthenticationResponse.builder().requestSuccess(false).requestMessage("User not found").build(); //return all false.
        }
    }


    public ResponseEntity<String> sendOTP(String receiver){
        receiver = "+88" + receiver;
        try{
            var user = userRepository.findByPhone(receiver).orElseThrow();

            //only send SMS if user exists. Signup can still send OTP as otp only sent after saving user.
            smsService.sendSMS(receiver);
            return ResponseEntity.ok("Verification SMS sent");
        }
        catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.ok("Failed to sent an SMS. Please try again");
        }
    }


    public AuthenticationResponse verifyOTP(String receiver, String verificationCode){
        receiver = "+88" + receiver;
        var user = userRepository.findByPhone(receiver).orElseThrow();

        boolean isApproved = smsService.checkVerificationCode(receiver, verificationCode); //verify code
        if(isApproved){
            //update user's status and create JWT token to login user directly.
            user.setVerificationStatus(PhoneNumberVerificationStatus.VERIFIED);
            userRepository.save(user);
            var jwtToken = jwtGeneratorService.generateToken(user);

            return AuthenticationResponse.builder().token(jwtToken).fullName(user.getFullName())
                    .verificationStatus(true).build();
        }
        else {
            return AuthenticationResponse.builder().token(null).fullName(null)
                    .verificationStatus(false).build(); //user can the retry with different code rather than requesting new code.
        }
    }


    public AuthenticationResponse changePassword(PasswordResetRequest request){
        String receiver = "+88" + request.getPhone();
        try{
            var user = userRepository.findByPhone(receiver).orElseThrow();

            //verify otp
            boolean isApproved = smsService.checkVerificationCode(receiver, request.getOtp()); //verify code
            if(isApproved){
                //update user's status and create JWT token to login user directly.
                user.setPassword(passwordEncoder.encode(request.getPassword()));
                userRepository.save(user);

                var jwtToken = jwtGeneratorService.generateToken(user);

                return AuthenticationResponse.builder().token(jwtToken).fullName(user.getFullName())
                        .verificationStatus(true).build();
            }
            else {
                return AuthenticationResponse.builder().requestSuccess(false).requestMessage("OTP didn't match").build();
            }
        }
        catch (Exception e){
            return AuthenticationResponse.builder().requestSuccess(false).requestMessage("No user found.").build();
        }
    }
}
