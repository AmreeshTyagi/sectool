package com.secfix.todos.services;

import com.secfix.todos.apis.dtos.UserDto;
import com.secfix.todos.apis.dtos.requests.UserCreateRequest;
import com.secfix.todos.apis.dtos.requests.UserUpdateRequest;
import com.secfix.todos.database.models.UserInfo;
import com.secfix.todos.database.repositories.UserInfoRepository;
import com.secfix.todos.exceptions.ApiServiceCallException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UsersManagementService {
    private Logger logger = LoggerFactory.getLogger(UsersManagementService.class);

    @Autowired
    private UserInfoRepository userInfoRepository;

    public List<UserDto> getUsers() {
        return this.userInfoRepository.findAll().stream()
                .map(u -> new UserDto(u))
                .collect(Collectors.toList());
    }

    public UserInfo getUserById(Integer userId) {
        logger.debug("Get user started, UserId: <{}>", userId);
        try {
            Optional<UserInfo> userRecord = this.userInfoRepository.findById(userId);

            if (!userRecord.isPresent()) {
                throw new ApiServiceCallException(
                        String.format("User with id <%s> not exist", userId),
                        HttpStatus.BAD_REQUEST);
            }

            return userRecord.get();
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;

            logger.error("Get user failed, UserId: <{}>", userId, ex);
            throw new ApiServiceCallException(
                    "Update user failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public UserDto getUserByIdInDto(Integer userId) {
        return new UserDto(this.getUserById(userId));
    }

    public UserDto createUser(UserCreateRequest request) {
        logger.info("Create user started, Data: <{}>", request.toString());
        try {
            UserInfo user = new UserInfo();
            user.setTenantId(request.getTenantId());
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setIsActive(request.getIsActive() == null ? true : request.getIsActive());
            user = this.userInfoRepository.save(user);

            logger.info("Create user completed, Data: <{}>", request.toString());
            return new UserDto(user);
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;
            logger.error("Create user failed, Data: <{}>", request.toString(), ex);
            throw new ApiServiceCallException(
                    "Create user failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public UserDto updateUser(Integer userId, UserUpdateRequest request) {
        logger.info("Update user started, UserId: <{}>, Data: <{}>", userId, request.toString());
        try {
            UserInfo user = this.getUserById(userId);

            if (StringUtils.isNotBlank(request.getName())) {
                user.setName(request.getName());
            }

            if (StringUtils.isNotBlank(request.getEmail())) {
                user.setEmail(request.getEmail());
            }

            if (request.getIsActive() != null) {
                user.setIsActive(request.getIsActive());
            }

            user = this.userInfoRepository.save(user);

            logger.info("Update user completed, UserId: <{}>, Data: <{}>", userId, user.toString());
            return new UserDto(user);
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;

            logger.error("Update user failed, UserId: <{}>, Data: <{}>", userId, request.toString(), ex);
            throw new ApiServiceCallException(
                    "Update user failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteUser(Integer userId) {
        logger.info("Delete user started, UserId: <{}>", userId);
        try {
            UserInfo user = this.getUserById(userId);

            this.userInfoRepository.delete(user);

            logger.info("Delete user completed, UserId: <{}>", userId);
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;
            logger.info("Delete user failed, UserId: <{}>", userId, ex);
            throw new ApiServiceCallException(
                    "Delete user failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
