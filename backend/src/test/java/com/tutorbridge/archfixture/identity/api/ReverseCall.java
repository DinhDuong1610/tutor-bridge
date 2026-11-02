package com.tutorbridge.archfixture.identity.api;

import com.tutorbridge.archfixture.classes.internal.ClassService;

public class ReverseCall {
    ClassService identityMustNotDependOnClasses = new ClassService();
}
