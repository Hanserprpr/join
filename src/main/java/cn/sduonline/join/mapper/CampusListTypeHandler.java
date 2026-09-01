package cn.sduonline.join.mapper;

import cn.sduonline.join.data.enums.Campus;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * 将部门校区列表以逗号分隔字符串的形式存入单个数据库列，
 * 与 {@link Campus} 的 {@code name()} 互相转换。
 */
@MappedTypes(List.class)
public class CampusListTypeHandler extends BaseTypeHandler<List<Campus>> {

    private static final String SEPARATOR = ",";

    @Override
    public void setNonNullParameter(
            PreparedStatement ps, int i, List<Campus> parameter, JdbcType jdbcType
    ) throws SQLException {
        ps.setString(i, toColumnValue(parameter));
    }

    @Override
    public List<Campus> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromColumnValue(rs.getString(columnName));
    }

    @Override
    public List<Campus> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromColumnValue(rs.getString(columnIndex));
    }

    @Override
    public List<Campus> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromColumnValue(cs.getString(columnIndex));
    }

    private static String toColumnValue(List<Campus> campuses) {
        if (campuses == null || campuses.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Campus campus : campuses) {
            if (campus == null) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(SEPARATOR);
            }
            sb.append(campus.name());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static List<Campus> fromColumnValue(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        List<Campus> campuses = new ArrayList<>();
        for (String token : value.split(SEPARATOR)) {
            if (!token.isBlank()) {
                campuses.add(Campus.fromValue(token.trim()));
            }
        }
        return campuses;
    }
}
