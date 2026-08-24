package cn.sduonline.join.service.poster;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.DepartmentPosterRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 海报 URL 白名单策略，按解析后的 origin 和路径边界匹配。 */
@Component
@RequiredArgsConstructor
public class PosterUrlPolicy {

    private final PosterStorage posterStorage;
    private final AppProperties appProperties;

    public void validateAll(List<DepartmentPosterRequest> posters) {
        if (posters == null) {
            return;
        }
        List<String> prefixes = new ArrayList<>();
        prefixes.add(posterStorage.publicUrlPrefix());
        prefixes.addAll(appProperties.getPoster().getAllowedUrlPrefixList());
        for (DepartmentPosterRequest poster : posters) {
            if (poster == null || !isAllowed(poster.url(), prefixes)) {
                throw new PosterUrlValidationException();
            }
        }
    }

    static boolean isAllowed(String value, List<String> prefixes) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            URI candidate = URI.create(value.trim());
            if (!isSafeHttpUrl(candidate)) {
                return false;
            }
            for (String prefixValue : prefixes) {
                if (!StringUtils.hasText(prefixValue)) {
                    continue;
                }
                URI prefix = URI.create(ensureTrailingSlash(prefixValue.trim()));
                if (isSafeHttpUrl(prefix) && sameOrigin(candidate, prefix)
                        && candidate.getRawPath().startsWith(prefix.getRawPath())) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSafeHttpUrl(URI uri) {
        String scheme = uri.getScheme();
        String rawPath = uri.getRawPath();
        if (scheme == null || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || rawPath == null) {
            return false;
        }
        if (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http")) {
            return false;
        }
        String lowerPath = rawPath.toLowerCase();
        return !rawPath.contains("\\")
                && !lowerPath.contains("%2e")
                && !lowerPath.contains("%2f")
                && !lowerPath.contains("%5c")
                && uri.normalize().equals(uri);
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }
}
