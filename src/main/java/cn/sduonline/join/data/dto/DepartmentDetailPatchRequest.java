package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.Campus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 部门详情部分更新请求
 * JsonSetter 同时记录字段是否出现，以区分“未传”和“显式传 null”
 */
public class DepartmentDetailPatchRequest {

    @Size(max = 6)
    private List<Campus> campuses;

    @Size(max = 10000)
    private String introduction;

    @Size(max = 20)
    private List<@Valid DepartmentPosterRequest> posters;

    @Size(max = 50)
    private List<@Valid DepartmentAchievementRequest> achievements;

    @Size(max = 10000)
    private String recruitmentRequirements;

    @Size(max = 1000)
    private String contact;

    @Size(max = 1000)
    private String recruitmentGroup;

    @JsonIgnore
    private boolean campusesPresent;
    @JsonIgnore
    private boolean introductionPresent;
    @JsonIgnore
    private boolean postersPresent;
    @JsonIgnore
    private boolean achievementsPresent;
    @JsonIgnore
    private boolean recruitmentRequirementsPresent;
    @JsonIgnore
    private boolean contactPresent;
    @JsonIgnore
    private boolean recruitmentGroupPresent;

    @JsonSetter
    public void setCampuses(List<Campus> campuses) {
        this.campusesPresent = true;
        this.campuses = campuses;
    }

    @JsonSetter
    public void setIntroduction(String introduction) {
        this.introductionPresent = true;
        this.introduction = introduction;
    }

    @JsonSetter
    public void setPosters(List<DepartmentPosterRequest> posters) {
        this.postersPresent = true;
        this.posters = posters;
    }

    @JsonSetter
    public void setAchievements(List<DepartmentAchievementRequest> achievements) {
        this.achievementsPresent = true;
        this.achievements = achievements;
    }

    @JsonSetter
    public void setRecruitmentRequirements(String recruitmentRequirements) {
        this.recruitmentRequirementsPresent = true;
        this.recruitmentRequirements = recruitmentRequirements;
    }

    @JsonSetter
    public void setContact(String contact) {
        this.contactPresent = true;
        this.contact = contact;
    }

    @JsonSetter
    public void setRecruitmentGroup(String recruitmentGroup) {
        this.recruitmentGroupPresent = true;
        this.recruitmentGroup = recruitmentGroup;
    }

    public List<Campus> getCampuses() {
        return campuses;
    }

    public String getIntroduction() {
        return introduction;
    }

    public List<DepartmentPosterRequest> getPosters() {
        return posters;
    }

    public List<DepartmentAchievementRequest> getAchievements() {
        return achievements;
    }

    public String getRecruitmentRequirements() {
        return recruitmentRequirements;
    }

    public String getContact() {
        return contact;
    }

    public String getRecruitmentGroup() {
        return recruitmentGroup;
    }

    @JsonIgnore
    public boolean isCampusesPresent() {
        return campusesPresent;
    }

    @JsonIgnore
    public boolean isIntroductionPresent() {
        return introductionPresent;
    }

    @JsonIgnore
    public boolean isPostersPresent() {
        return postersPresent;
    }

    @JsonIgnore
    public boolean isAchievementsPresent() {
        return achievementsPresent;
    }

    @JsonIgnore
    public boolean isRecruitmentRequirementsPresent() {
        return recruitmentRequirementsPresent;
    }

    @JsonIgnore
    public boolean isContactPresent() {
        return contactPresent;
    }

    @JsonIgnore
    public boolean isRecruitmentGroupPresent() {
        return recruitmentGroupPresent;
    }

}
