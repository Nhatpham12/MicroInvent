package com.microinvent.catalog.web.dto;

public record ProductGroupResponse(Integer id, String code, String name, String description, long productCount) {
}
