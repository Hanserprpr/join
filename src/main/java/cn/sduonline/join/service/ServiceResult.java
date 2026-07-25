package cn.sduonline.join.service;

import cn.sduonline.join.data.enums.BizCode;

public record ServiceResult<T>(T data, BizCode error) {

    public static <T> ServiceResult<T> success(T data) {
        return new ServiceResult<>(data, null);
    }

    public static <T> ServiceResult<T> failure(BizCode error) {
        return new ServiceResult<>(null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }
}
